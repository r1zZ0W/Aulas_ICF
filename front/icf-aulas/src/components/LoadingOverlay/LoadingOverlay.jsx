import "./LoadingOverlay.css";

export default function LoadingOverlay({ label = "Cargando..." }) {
  return (
    <div className="loading-overlay" role="status" aria-label={label}>
      <div className="icf-spinner">
        <div className="icf-spinner__ring" />
        <div className="icf-spinner__center" />
        <div className="icf-spinner__orbit">
          <div className="icf-spinner__ball" />
        </div>
      </div>
      <p className="loading-overlay__label">{label}</p>
    </div>
  );
}
