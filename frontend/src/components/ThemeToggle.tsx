import { useTheme } from '../context/ThemeContext';

export const ThemeToggle = () => {
  const { theme, setTheme } = useTheme();

  return (
    <select
      value={theme}
      onChange={(e) => setTheme(e.target.value as any)}
      className="p-2 rounded-md bg-secondary text-secondary-foreground border border-border focus:ring-2 focus:ring-ring outline-none transition-colors cursor-pointer"
      aria-label="Select theme"
    >
      <option value="light">Light</option>
      <option value="dark">Dark</option>
      <option value="oled">OLED</option>
    </select>
  );
};
