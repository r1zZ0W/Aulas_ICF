/**
 * Header component for the login form.
 * Shows the logo of the application.
 * @param {string} containerClassName - The class name for the logo header container.
 */
export default function LogoHeader({ containerClassName = "text-end mb-4" }) {
  return (
    <div className={containerClassName}>
      <div className="d-flex align-items-center justify-content-center">
        <img
          src="/src/assets/aulas_header.png"
          alt="Logo Ithera"
          className="img-fluid"
        />
      </div>
    </div>
  );
}
