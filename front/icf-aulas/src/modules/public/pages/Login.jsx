import { useState, useEffect } from "react";
import { useLocation } from "react-router-dom";
import Button from "../../../components/Button/Button";
import InputField from "../components/InputField";
import PasswordInput from "../components/PasswordInput";
import LogoHeader from "../components/LogoHeader";
import ProgressBar from "../components/ProgressBar";
import LoadingOverlay from "../../../components/LoadingOverlay/LoadingOverlay";
import Modal from "../../../components/Modal/Modal";
import { useLogin } from "../../../hooks/useLogin";

import "../styles/public.css";
import { AtomIcon } from "lucide-react";
import logoIcfPng from "../../../assets/logo_icf.png";

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
        <div style={modalStyles.container}>
          <div style={modalStyles.iconBox}>
            <svg width="38" height="38" viewBox="0 0 24 24" fill="none">
              <path d="M7 10V8a5 5 0 0 1 10 0v2" stroke="#2563eb" strokeWidth="1.8" strokeLinecap="round" />
              <rect x="4" y="10" width="16" height="10" rx="2.5" stroke="#2563eb" strokeWidth="1.8" />
              <circle cx="12" cy="15" r="1.2" fill="#2563eb" />
            </svg>
          </div>
          <h2 style={modalStyles.title}>{sessionModal?.title}</h2>
          <p style={modalStyles.message}>{sessionModal?.message}</p>
          <button
            style={modalStyles.btn}
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
                  src={logoIcfPng}
                  alt="Logo ICF"
                  className="img-fluid illustration-img"
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
            <div className="col-sm-5 p-5 bg-white">
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

const modalStyles = {
  container: {
    padding: '40px 32px',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    width: '520px',
    maxWidth: '100%',
    textAlign: 'center',
    fontFamily: 'Inter, system-ui, sans-serif',
  },
  iconBox: {
    width: '80px',
    height: '80px',
    margin: '0 auto 20px',
    borderRadius: '50%',
    background: '#eaf2ff',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    margin: '0 0 12px',
    fontSize: '28px',
    fontWeight: 800,
    color: '#0f172a',
  },
  message: {
    margin: '0 auto 28px',
    fontSize: '15px',
    lineHeight: 1.65,
    color: '#475569',
    maxWidth: '400px',
  },
  btn: {
    padding: '11px 20px',
    borderRadius: '10px',
    border: 'none',
    background: '#2563eb',
    color: '#fff',
    fontWeight: 600,
    fontSize: '14px',
    cursor: 'pointer',
  },
};
