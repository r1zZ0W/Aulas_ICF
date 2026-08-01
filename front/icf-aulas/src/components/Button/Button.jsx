import { isValidElement } from "react";
import Icon from "../Icon/Icon";
import "./Button.css";

const VARIANTS = {
  primary: "btn--primary",
  secondary: "btn--secondary",
  outline: "btn--outline",
  tertiary: "btn--tertiary",
  ghost: "btn--ghost",
};

/**
 * Reusable animated button with multiple visual variants, sizes, and optional flanking icons.
 *
 * @param {string} [text] - Label text (alternative to children)
 * @param {React.ReactNode} [children] - Content rendered inside the button (takes precedence over text)
 * @param {Function} [onClick] - Click handler
 * @param {'button'|'submit'|'reset'} [type='button'] - HTML button type attribute
 * @param {'primary'|'secondary'|'outline'|'tertiary'|'ghost'} [variant='primary'] - Visual style
 * @param {'small'|'medium'|'large'} [size='medium'] - Size modifier applied via CSS class
 * @param {string|React.ReactNode} [iconLeft] - Icon rendered to the left of the label
 * @param {string|React.ReactNode} [iconRight] - Icon rendered to the right of the label
 * @param {number} [iconSize=30] - Pixel size for string-based icon names (passed to Icon component)
 * @param {boolean} [disabled] - Disables the button and suppresses the press animation
 * @param {boolean} [fullWidth] - Stretches the button to fill its parent container
 * @param {string} [className=''] - Additional CSS classes appended to the button element
 * @param {string} [title] - Tooltip text shown on hover (accessibility)
 */
export default function Button({
  text,
  children,
  onClick,
  type = "button",
  variant = "primary",
  size = "medium",
  iconLeft,
  iconRight,
  iconSize = 30,
  disabled,
  fullWidth,
  className = "",
  title,
  ...rest
}) {
  const content = children ?? text;
  const variantClass = VARIANTS[variant] ?? VARIANTS.primary;

  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      title={title}
      className={`btn btn--${size} ${variantClass} ${fullWidth ? "btn--full" : ""} ${className}`.trim()}
      {...rest}
    >
      {iconLeft && (
        <span className="btn__icon btn__icon--left">
          {isValidElement(iconLeft) ? (
            iconLeft
          ) : (
            <Icon icon={iconLeft} size={iconSize} />
          )}
        </span>
      )}
      {content}
      {iconRight && (
        <span className="btn__icon btn__icon--right">
          {isValidElement(iconRight) ? (
            iconRight
          ) : (
            <Icon icon={iconRight} size={iconSize} />
          )}
        </span>
      )}
    </button>
  );
}
