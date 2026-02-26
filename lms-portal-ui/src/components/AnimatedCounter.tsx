import { useEffect, useState, useRef } from "react";

interface AnimatedCounterProps {
  value: number;
  duration?: number;
  prefix?: string;
  suffix?: string;
  format?: "currency" | "number" | "percent";
}

export function AnimatedCounter({ value, duration = 1200, prefix = "", suffix = "", format = "number" }: AnimatedCounterProps) {
  const [displayed, setDisplayed] = useState(0);
  const rafRef = useRef<number>();
  const startRef = useRef<number>();

  useEffect(() => {
    startRef.current = performance.now();
    const animate = (now: number) => {
      const elapsed = now - (startRef.current || now);
      const progress = Math.min(elapsed / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3); // ease-out cubic
      setDisplayed(value * eased);
      if (progress < 1) {
        rafRef.current = requestAnimationFrame(animate);
      }
    };
    rafRef.current = requestAnimationFrame(animate);
    return () => { if (rafRef.current) cancelAnimationFrame(rafRef.current); };
  }, [value, duration]);

  const formatValue = () => {
    if (format === "currency") {
      if (displayed >= 1_000_000_000) return `${(displayed / 1_000_000_000).toFixed(1)}B`;
      if (displayed >= 1_000_000) return `${(displayed / 1_000_000).toFixed(1)}M`;
      if (displayed >= 1_000) return `${(displayed / 1_000).toFixed(0)}K`;
      return displayed.toFixed(0);
    }
    if (format === "percent") return displayed.toFixed(1);
    if (displayed >= 1_000) return Math.floor(displayed).toLocaleString();
    return Math.floor(displayed).toString();
  };

  return (
    <span className="font-mono tabular-nums">
      {prefix}{prefix && " "}{formatValue()}{suffix}
    </span>
  );
}
