import { useEffect, useRef } from 'react';
export function AudioPlayer({ audio, onEnded, onCancelled }: { audio?: Blob; onEnded?: () => void; onCancelled?: () => void }) {
  const playerRef = useRef<HTMLAudioElement>(null);
  useEffect(() => {
    if (!audio || !playerRef.current) return;
    const url = URL.createObjectURL(audio);
    playerRef.current.src = url;
    return () => {
      playerRef.current?.pause();
      if (playerRef.current) playerRef.current.removeAttribute('src');
      URL.revokeObjectURL(url);
    };
  }, [audio]);
  return <div className="audio-player"><audio ref={playerRef} controls onEnded={onEnded} /><button type="button" className="button button-ghost" disabled={!audio} onClick={() => { playerRef.current?.pause(); onCancelled?.(); }}>停止播放</button><small>问题文本始终可见；停止播放不会推进业务状态。</small></div>;
}
