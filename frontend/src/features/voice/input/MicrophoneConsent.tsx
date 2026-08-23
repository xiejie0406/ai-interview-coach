export function MicrophoneConsent({ granted, onChange }: { granted: boolean; onChange: (granted: boolean) => void }) {
  return (
    <section className="voice-consent" aria-labelledby="voice-consent-title">
      <div>
        <span className="card-kicker">VOICE CONSENT</span>
        <h3 id="voice-consent-title">在采集前确认用途</h3>
        <p className="muted">这是本轮浏览器采集确认，不替代服务端治理同意。供应商、地域和删除期限必须由 preflight 返回；拒绝时不会请求麦克风。</p>
      </div>
      <label className="consent-check">
        <input type="checkbox" checked={granted} onChange={(event) => onChange(event.target.checked)} />
        <span>我允许浏览器在本轮请求麦克风；服务端仍需再次校验同意</span>
      </label>
    </section>
  );
}
