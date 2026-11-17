import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { CircleHelp } from 'lucide-react';

export default function InfoTooltip({ label, children }: { label: string; children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const [pinned, setPinned] = useState(false);
  const root = useRef<HTMLSpanElement>(null);
  const popup = useRef<HTMLSpanElement>(null);
  const [position, setPosition] = useState({ left: 12, top: 80, width: 320 });
  const id = useId();
  useLayoutEffect(() => {
    if (!open || !root.current || !popup.current) return;
    const anchor = root.current.getBoundingClientRect();
    const width = Math.min(340, window.innerWidth - 24);
    const height = popup.current.getBoundingClientRect().height;
    const left = Math.max(12, Math.min(anchor.left - 110, window.innerWidth - width - 12));
    const top = anchor.bottom + height + 12 < window.innerHeight ? anchor.bottom + 8 : Math.max(12, anchor.top - height - 8);
    setPosition({ left, top, width });
  }, [open]);
  useEffect(() => {
    if (!open) return;
    function close(event: PointerEvent) {
      if (!root.current?.contains(event.target as Node)) { setOpen(false); setPinned(false); }
    }
    document.addEventListener('pointerdown', close);
    return () => document.removeEventListener('pointerdown', close);
  }, [open]);
  return <span className="info-tooltip" ref={root}
    onMouseEnter={() => setOpen(true)} onMouseLeave={() => { if (!pinned) setOpen(false); }}
    onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget)) { setOpen(false); setPinned(false); } }}>
    <button type="button" className="info-button" aria-label={`${label} 설명`} aria-expanded={open} aria-describedby={open ? id : undefined}
      onFocus={event => { if (event.currentTarget.matches(':focus-visible')) setOpen(true); }}
      onClick={() => { setPinned(!pinned); setOpen(!pinned); }}
      onKeyDown={event => { if (event.key === 'Escape') { setOpen(false); setPinned(false); } }}><CircleHelp size={15} /></button>
    {open && <span className="info-popup" ref={popup} style={position} role="tooltip" id={id}><strong>{label}</strong>{children}<span className="tooltip-hint">다시 누르거나 Esc 키로 닫기</span></span>}
  </span>;
}
