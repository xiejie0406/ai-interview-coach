[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^aden-collector-[A-Za-z0-9-]{1,120}$')][string]$PipeName)

# 独立当前用户管道代理：仅传输有界帧，业务权限仍由 Electron/RuoYi 裁决。
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$utf8 = [Text.UTF8Encoding]::new($false, $true)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
$pipeSecurity = [IO.Pipes.PipeSecurity]::new()
$pipeSecurity.SetOwner($currentSid)
$pipeSecurity.SetAccessRuleProtection($true, $false)
$pipeSecurity.AddAccessRule([IO.Pipes.PipeAccessRule]::new([Security.Principal.SecurityIdentifier]::new('S-1-5-2'), [IO.Pipes.PipeAccessRights]::FullControl, [Security.AccessControl.AccessControlType]::Deny))
$pipeSecurity.AddAccessRule([IO.Pipes.PipeAccessRule]::new($currentSid, [IO.Pipes.PipeAccessRights]::FullControl, [Security.AccessControl.AccessControlType]::Allow))
$pipeSecurity.AddAccessRule([IO.Pipes.PipeAccessRule]::new([Security.Principal.SecurityIdentifier]::new('S-1-5-18'), [IO.Pipes.PipeAccessRights]::FullControl, [Security.AccessControl.AccessControlType]::Allow))
$maxFrame = 262144

function Read-Exact([IO.Stream]$Stream, [int]$Length) {
    $bytes = [byte[]]::new($Length)
    $offset = 0
    while ($offset -lt $Length) {
        $count = $Stream.Read($bytes, $offset, $Length - $offset)
        if ($count -eq 0) { throw [IO.EndOfStreamException]::new() }
        $offset += $count
    }
    return ,$bytes
}

while ($true) {
    $pipe = [IO.Pipes.NamedPipeServerStream]::new($PipeName, [IO.Pipes.PipeDirection]::InOut, 1, [IO.Pipes.PipeTransmissionMode]::Byte, [IO.Pipes.PipeOptions]::None, 65536, 65536, $pipeSecurity)
    try {
        $actualRules = $pipe.GetAccessControl().GetAccessRules($true, $false, [Security.Principal.SecurityIdentifier])
        $networkDenied = @($actualRules | Where-Object { $_.IdentityReference.Value -eq 'S-1-5-2' -and $_.AccessControlType -eq [Security.AccessControl.AccessControlType]::Deny }).Count -eq 1
        if (-not $networkDenied) { throw 'Network deny ACL missing' }
        [Console]::Out.WriteLine('{"kind":"ready","networkDenied":true}')
        $pipe.WaitForConnection()
        [Console]::Out.WriteLine('{"kind":"connected"}')
        while ($pipe.IsConnected) {
            $header = Read-Exact $pipe 4
            $length = [BitConverter]::ToUInt32($header, 0)
            if ($length -eq 0 -or $length -gt $maxFrame) { throw 'Frame limit exceeded' }
            $body = $utf8.GetString((Read-Exact $pipe ([int]$length)))
            # Parse once to reject framing tricks/newlines before forwarding a compact JSON line.
            $message = $body | ConvertFrom-Json
            $forward = @{ kind='message'; message=$message } | ConvertTo-Json -Depth 30 -Compress
            [Console]::Out.WriteLine($forward)
            $response = [Console]::In.ReadLine()
            if ($null -eq $response) { exit 0 }
            $data = $utf8.GetBytes($response)
            if ($data.Length -gt $maxFrame) { throw 'Response limit exceeded' }
            $pipe.Write([BitConverter]::GetBytes([uint32]$data.Length), 0, 4)
            $pipe.Write($data, 0, $data.Length)
            $pipe.Flush()
        }
    } catch [IO.EndOfStreamException] {
        # 正常断连，不输出消息正文或凭据。
    } catch {
        [Console]::Error.WriteLine('COLLECTOR_PIPE_CONNECTION_FAILED')
    } finally {
        $pipe.Dispose()
        [Console]::Out.WriteLine('{"kind":"disconnected"}')
    }
}
