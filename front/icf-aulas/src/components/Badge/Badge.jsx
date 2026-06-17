import './Badge.css';

/**
 * Pill badge for role/status display in tables.
 *
 * @param {'success'|'danger'|'primary'|'neutral'} variant
 * @param {React.ReactNode} children
 * @param {string} [className='']
 */
export default function Badge({ variant = 'neutral', children, className = '' }) {
  return (
    <span className={`badge badge--${variant} ${className}`.trim()}>
      {children}
    </span>
  );
}
