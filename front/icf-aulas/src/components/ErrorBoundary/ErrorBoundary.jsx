import { Component } from "react";
import Button from "../Button/Button";
import fantasmitaSvg from "../../assets/fantasmita.svg";
import "./ErrorBoundary.css";

/**
 * React class-based error boundary that catches unhandled runtime errors in its child tree
 * and renders a friendly fallback UI instead of leaving the page blank. Provides retry
 * (re-render subtree) and go-home (redirect to /) recovery actions.
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null, errorInfo: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error("Error caught by ErrorBoundary:", error, errorInfo);
    this.setState({ errorInfo });
  }

  handleRetry = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
  };

  handleGoHome = () => {
    window.location.href = "/";
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="error-boundary min-vh-100 d-flex align-items-center justify-content-center p-4">
          <div className="error-boundary__card w-100 text-center px-4 py-5">
            <img
              src={fantasmitaSvg}
              alt=""
              width={140}
              height={140}
              aria-hidden
              className="error-boundary__illustration mb-3"
            />
            <h1 className="error-boundary__title mb-2">¡Ups! Algo no salió bien</h1>
            <p className="error-boundary__message mb-4">
              Encontramos un problema inesperado en esta pantalla. No se perdió ninguna
              información — intenta de nuevo o vuelve al inicio.
            </p>
            <div className="error-boundary__actions d-flex flex-wrap gap-3 justify-content-center">
              <Button variant="outline" onClick={this.handleRetry}>
                Intentar de nuevo
              </Button>
              <Button variant="primary" onClick={this.handleGoHome}>
                Ir al inicio
              </Button>
            </div>

            {import.meta.env.DEV && this.state.error && (
              <details className="error-boundary__details mt-4 text-start">
                <summary>Detalles técnicos (solo visible en desarrollo)</summary>
                <pre>{this.state.error.stack ?? String(this.state.error)}</pre>
                {this.state.errorInfo?.componentStack && (
                  <pre>{this.state.errorInfo.componentStack}</pre>
                )}
              </details>
            )}
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
