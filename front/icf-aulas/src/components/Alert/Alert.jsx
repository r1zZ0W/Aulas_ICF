import './Alert.css';

/**
 * Inline contextual notice for surface-level status messages.
 *
 * Designed to match the visual language of {@link Badge} (same font,
 * similar color tokens) while occupying a full-width row rather than
 * an inline chip.
 *
 * @param {'warning'|'info'} [variant='info'] - Color variant.
 * @param {React.ReactNode} children
 * @param {string} [className='']
 */
export default function Alert({ variant = 'info', children, className = '' }) {
  return (
    <div className={`alert alert--${variant} ${className}`.trim()} role="alert">
      {children}
    </div>
  );
}
