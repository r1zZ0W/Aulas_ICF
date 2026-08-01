import { useState, useEffect } from "react";
import { useLocation } from "react-router-dom";
import Button from "../../../components/Button/Button";
import InputField from "../components/InputField";
import PasswordInput from "../components/PasswordInput";
import LogoHeader from "../components/LogoHeader";
import LoadingOverlay from "../../../components/LoadingOverlay/LoadingOverlay";
import Modal from "../../../components/Modal/Modal";
import { useLogin } from "../../../hooks/useLogin";

import "../styles/public.css";
// Lossless WebP re-encode of logo_icf.png (729x654, 51.7 KB -> 29.2 KB, -44%). Verified
// pixel-identical to the PNG on every non-fully-transparent pixel and on the alpha channel
// itself, so [data-theme="dark"] .illustration-img's mix-blend-mode/invert filter (see
// public.css) renders exactly as before — the only bytes that differ are RGB values on
// fully-transparent (alpha=0) pixels, which no compositing path can ever expose.
import logoIcfWebp from "../../../assets/logo_icf.webp";

/** Maps an auth reason to a human-readable modal content. */
const SESSION_MODAL_CONTENT = {
  'no-session': {
    title: 'Sesión requerida',
    message: 'Necesitas iniciar sesión para acceder a esa sección.',
  },
  'revoked': {
    title: 'Sesión expirada',
    message: 'Tu sesión expiró o el token ya no es válido. Inicia sesión nuevamente.',
  },
};

/**
 * Login page rendered at /login.
 *
 * Delegates all form logic (state, validation, API call, session storage, and
 * role-based redirect) to {@link useLogin}. This component is responsible only
 * for layout and rendering.
 *
 * An optional success message (e.g., after a password reset) is shown when
 * the previous route passes it through React Router location state.
 *
 * A modal is displayed when the user arrives here due to a missing/expired
 * session (`location.state.reason` or `sessionStorage.authReason`), but NOT
 * when they explicitly clicked "Cerrar Sesión" (authReason === 'logout').
 */
export default function Login() {
  const { form, errors, loading, handleChange, handleBlur, handleSubmit } = useLogin();

  const location = useLocation();
  const successMessage = location.state?.success;

  const [sessionModal, setSessionModal] = useState(null);

  useEffect(() => {
    // Read and immediately clear the reason so it doesn't re-appear on refresh.
    const storedReason = sessionStorage.getItem('authReason');
    sessionStorage.removeItem('authReason');

    // Intentional logout → no modal.
    if (storedReason === 'logout') return;

    // Resolve the reason: sessionStorage (hard-reload path) takes priority over
    // location.state (React-Router navigation path).
    const reason = storedReason || location.state?.reason;
    if (reason && SESSION_MODAL_CONTENT[reason]) {
      setSessionModal(SESSION_MODAL_CONTENT[reason]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <>
      {loading && <LoadingOverlay label="Ingresando..." />}

      {/* Modal de sesión requerida / expirada */}
      <Modal open={!!sessionModal} className="w-auto">
        <div className="session-modal">
          <div className="session-modal__icon-box">
            <svg width="38" height="38" viewBox="0 0 24 24" fill="none" className="session-modal__icon-svg">
              <path d="M7 10V8a5 5 0 0 1 10 0v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
              <rect x="4" y="10" width="16" height="10" rx="2.5" stroke="currentColor" strokeWidth="1.8" />
              <circle cx="12" cy="15" r="1.2" fill="currentColor" />
            </svg>
          </div>
          <h2 className="session-modal__title">{sessionModal?.title}</h2>
          <p className="session-modal__message">{sessionModal?.message}</p>
          <button
            className="session-modal__btn"
            onClick={() => setSessionModal(null)}
            autoFocus
          >
            Entendido
          </button>
        </div>
      </Modal>
      <main className="bg-custom-layout">
        <div className="card-container">
          <div className="row g-0 flex-grow-1">
            {/* Left decorative panel */}
            <div className="col-sm-7 open-box d-md-flex align-items-center justify-content-center transition-all side-panel">
              <div className="text-center w-100">
                <img
                  src={logoIcfWebp}
                  alt="Logo ICF"
                  className="img-fluid illustration-img"
                  width="729"
                  height="654"
                  fetchpriority="high"
                  decoding="async"
                />
                <div className="container mt-3">
                  <h1 className="fw-bolder">Sistema de Reservas</h1>
                  <p className="fw-medium">
                    Gestiona todas tus reservas de forma rápida y eficiente.
                  </p>
                </div>
              </div>
            </div>

            {/* Right panel — login form */}
            <div className="col-sm-5 p-5 login-form-panel">
              <div
                className="d-flex flex-column h-100 justify-content-center mt-4"
                style={{ width: "33vw", marginLeft: "1vw" }}
              >
                <LogoHeader containerClassName="container text-end mb-4" />

                <form onSubmit={handleSubmit}>
                  <div className="container me-5 p-4">
                    {successMessage && (
                      <p className="form-message form-message--success mb-3 text-center">
                        {successMessage}
                      </p>
                    )}

                    {errors._form && (
                      <p className="form-message form-message--error mb-3 text-center">
                        {errors._form}
                      </p>
                    )}

                    <InputField
                      label="Usuario"
                      type="text"
                      placeholder="Ingresa tu usuario"
                      labelClassName="login-form-label"
                      fullWidth
                      value={form.username}
                      onChange={handleChange("username")}
                      onBlur={handleBlur("username")}
                      error={errors.username}
                    />

                    <PasswordInput
                      label="Contraseña"
                      placeholder="Ingresa la contraseña"
                      labelClassName="login-form-label"
                      fullWidth
                      value={form.password}
                      onChange={handleChange("password")}
                      onBlur={handleBlur("password")}
                      error={errors.password}
                    />

                    <Button
                      text={loading ? "Ingresando..." : "Iniciar sesión"}
                      fullWidth
                      type="submit"
                      disabled={loading}
                    />
                  </div>
                </form>
              </div>
            </div>
          </div>
        </div>
      </main>
    </>
  );
}
