export function MediaToggleButton({
  enabled,
  onClick,
  label,
  enabledIcon,
  disabledIcon,
}: {
  enabled: boolean;
  onClick: () => void;
  label: string;
  enabledIcon: React.ReactNode;
  disabledIcon: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={enabled}
      aria-label={label}
      title={label}
      className={`flex h-11 w-11 items-center justify-center rounded-full border transition-colors ${
        enabled
          ? "border-input-border bg-input-bg text-foreground hover:bg-black/5 dark:hover:bg-white/10"
          : "border-danger-border bg-danger-bg text-danger"
      }`}
    >
      {enabled ? enabledIcon : disabledIcon}
    </button>
  );
}
