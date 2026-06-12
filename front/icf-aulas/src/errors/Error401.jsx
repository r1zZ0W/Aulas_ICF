import { Link } from 'react-router-dom';

export default function Error401() {
    return (
        <div style={styles.wrapper}>
            <div style={styles.card}>
                <div style={styles.iconBox}>
                    <svg width="38" height="38" viewBox="0 0 24 24" fill="none">
                        <path d="M7 10V8a5 5 0 0 1 10 0v2" stroke="#2563eb" strokeWidth="1.8" strokeLinecap="round"/>
                        <rect x="4" y="10" width="16" height="10" rx="2.5" stroke="#2563eb" strokeWidth="1.8"/>
                        <circle cx="12" cy="15" r="1.2" fill="#2563eb"/>
                    </svg>
                </div>
                <p style={styles.code}>ERROR 401</p>
                <h1 style={styles.title}>Sesión no válida</h1>
                <p style={styles.message}>
                    Necesitas iniciar sesión para acceder a esta sección.
                    Tu sesión pudo haber expirado o el token ya no es válido.
                </p>
                <div style={styles.actions}>
                    <Link to="/login" style={{ ...styles.btn, ...styles.primary }}>
                        Ir a iniciar sesión
                    </Link>
                </div>
            </div>
        </div>
    );
}

const styles = {
    wrapper: {
        minHeight: '100vh', display: 'flex', alignItems: 'center',
        justifyContent: 'center', background: '#f7f9fc',
        padding: '24px', fontFamily: 'Inter, system-ui, sans-serif'
    },
    card: {
        width: '100%', maxWidth: '520px', background: '#fff',
        border: '1px solid #e5e7eb', borderRadius: '20px',
        padding: '40px 32px', boxShadow: '0 12px 40px rgba(15,23,42,0.08)',
        textAlign: 'center'
    },
    iconBox: {
        width: '80px', height: '80px', margin: '0 auto 20px',
        borderRadius: '50%', background: '#eaf2ff',
        display: 'flex', alignItems: 'center', justifyContent: 'center'
    },
    code: { margin: '0 0 8px', fontSize: '13px', fontWeight: 700, color: '#2563eb', letterSpacing: '0.1em' },
    title: { margin: '0 0 12px', fontSize: '28px', fontWeight: 800, color: '#0f172a' },
    message: { margin: '0 auto 28px', fontSize: '15px', lineHeight: 1.65, color: '#475569', maxWidth: '400px' },
    actions: { display: 'flex', gap: '12px', justifyContent: 'center', flexWrap: 'wrap' },
    btn: { textDecoration: 'none', padding: '11px 20px', borderRadius: '10px', fontWeight: 600, fontSize: '14px' },
    primary: { background: '#2563eb', color: '#fff' }
};