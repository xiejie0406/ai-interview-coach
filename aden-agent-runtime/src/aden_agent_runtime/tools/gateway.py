"""不执行任何真实工具的 manifest 门禁。"""

from aden_agent_runtime.contracts import ToolManifest


class ToolPolicyError(RuntimeError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


class ToolGateway:
    def __init__(self, runtime_allowlist: frozenset[str]):
        self._runtime_allowlist = runtime_allowlist

    def effective_tools(self, manifest: ToolManifest) -> frozenset[str]:
        """服务端 manifest 与本地 allowlist 取交集，永不扩大服务端授权。"""

        return frozenset(
            tool.name
            for tool in manifest.tools
            if tool.name in self._runtime_allowlist and tool.max_calls > 0
        )

    def require_call(self, name: str, effective_tools: frozenset[str]) -> None:
        if name not in effective_tools:
            raise ToolPolicyError("AGENT_TOOL_NOT_AUTHORIZED")
