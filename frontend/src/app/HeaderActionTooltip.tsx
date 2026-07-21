import type { ReactNode } from 'react';

interface HeaderActionTooltipProps {
  children: ReactNode;
  className?: string;
  id: string;
  label: string;
}

export default function HeaderActionTooltip({ children, className, id, label }: HeaderActionTooltipProps) {
  return (
    <span className={`header-action-tooltip-wrap${className ? ` ${className}` : ''}`}>
      {children}
      <span className="header-action-tooltip" id={id} role="tooltip">
        {label}
      </span>
    </span>
  );
}
