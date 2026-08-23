import { useState } from 'react';

export type ConfidenceRange = { start: number; end: number };

export function TranscriptEditor({ initialText, lowConfidenceRanges, onConfirm, onCancel }: {
  initialText: string;
  lowConfidenceRanges: ConfidenceRange[];
  onConfirm: (correctedText: string, lowConfidenceAcknowledged: boolean) => void;
  onCancel: () => void;
}) {
  const [text, setText] = useState(initialText);
  const [acknowledged, setAcknowledged] = useState(lowConfidenceRanges.length === 0);
  return (
    <section className="transcript-editor">
      <div className="section-heading"><div><span className="card-kicker">CONFIRM TRANSCRIPT</span><h3>确认后才进入评测</h3></div><span className="status-chip">{lowConfidenceRanges.length} 处低置信</span></div>
      <textarea rows={8} value={text} onChange={(event) => setText(event.target.value)} aria-label="可修正的转写文本" />
      {lowConfidenceRanges.length > 0 && <label className="consent-check"><input type="checkbox" checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} /><span>我已检查低置信技术术语</span></label>}
      <div className="form-actions"><button type="button" className="button button-ghost" onClick={onCancel}>重录或改用文本</button><button type="button" className="button button-primary" disabled={!text.trim() || !acknowledged} onClick={() => onConfirm(text.trim(), acknowledged)}>确认转写</button></div>
    </section>
  );
}
