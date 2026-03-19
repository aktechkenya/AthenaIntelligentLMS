import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

interface PeriodSelectorProps {
  year: number;
  month: number;
  onYearChange: (year: number) => void;
  onMonthChange: (month: number) => void;
}

const months = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];

const currentYear = new Date().getFullYear();
const years = Array.from({ length: 5 }, (_, i) => currentYear - i);

export const PeriodSelector = ({ year, month, onYearChange, onMonthChange }: PeriodSelectorProps) => (
  <div className="flex items-center gap-2">
    <Select value={String(month)} onValueChange={(v) => onMonthChange(Number(v))}>
      <SelectTrigger className="w-[140px] h-8 text-xs">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {months.map((m, i) => (
          <SelectItem key={i + 1} value={String(i + 1)} className="text-xs">
            {m}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
    <Select value={String(year)} onValueChange={(v) => onYearChange(Number(v))}>
      <SelectTrigger className="w-[90px] h-8 text-xs">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {years.map((y) => (
          <SelectItem key={y} value={String(y)} className="text-xs">
            {y}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  </div>
);
