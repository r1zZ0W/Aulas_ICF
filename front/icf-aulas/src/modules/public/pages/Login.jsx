import { useLocation } from "react-router-dom";
import Button from "../../../components/Button/Button";
import InputField from "../components/InputField";
import PasswordInput from "../components/PasswordInput";
import LogoHeader from "../components/LogoHeader";
import ProgressBar from "../components/ProgressBar";
import LoadingOverlay from "../../../components/LoadingOverlay/LoadingOverlay";
import { useLogin } from "../../../hooks/useLogin";

import "../styles/public.css";

/**
 * Login page rendered at /login.
 *
 * Delegates all form logic (state, validation, API call, session storage, and
 * role-based redirect) to {@link useLogin}. This component is responsible only
 * for layout and rendering.
 *
 * An optional success message (e.g., after a password reset) is shown when
 * the previous route passes it through React Router location state.
 */
export default function Login() {
  const { form, errors, loading, handleChange, handleSubmit } = useLogin();

  const location = useLocation();
  const successMessage = location.state?.success;

  return (
    <>
    {loading && <LoadingOverlay label="Ingresando..." />}
    <main className="bg-custom-layout">
      <div className="card-container">
        <div className="row g-0 flex-grow-1">
          {/* Left decorative panel */}
          <div className="col-sm-7 open-box d-md-flex align-items-center justify-content-center transition-all side-panel">
            <div className="text-center w-100">
              <img
                src="/src/assets/logo_icf.png"
                alt="Logo ICF"
                className="img-fluid illustration-img"
              />
              <div className="container mt-3">
                <h1 className="fw-bolder">Sistema de Reservas</h1>
                <p className="fw-medium">
                  Gestiona todas tus reservas de forma rápida y eficiente.
                </p>
                <ProgressBar step={1} containerClassName="progress" />
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
                    error={errors.username}
                  />

                  <PasswordInput
                    label="Contraseña"
                    placeholder="Ingresa la contraseña"
                    labelClassName="login-form-label"
                    fullWidth
                    value={form.password}
                    onChange={handleChange("password")}
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
