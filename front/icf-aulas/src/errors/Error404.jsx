import { Link } from 'react-router-dom';

export default function Error404() {
    return (
        <div style={styles.wrapper}>
            <div style={styles.card}>
                <div style={styles.iconBox}>
                    <svg width="38" height="38" viewBox="0 0 24 24" fill="none">
                        <circle cx="11" cy="11" r="6" stroke="#2563eb" strokeWidth="1.8"/>
                        <path d="M20 20L16.65 16.65" stroke="#2563eb" strokeWidth="1.8" strokeLinecap="round"/>
                    </svg>
                </div>
                <p style={styles.code}>ERROR 404</p>
                <h1 style={styles.title}>Página no encontrada</h1>
                <p style={styles.message}>
                    La ruta que intentas abrir no existe, fue movida
                    o ya no está disponible dentro del sistema.
                </p>
                <div style={styles.actions}>
                    <Link to="/dashboard" style={{ ...styles.btn, ...styles.primary }}>
                        Volver al dashboard
                    </Link>
                    <Link to="/reservations" style={{ ...styles.btn, ...styles.secondary }}>
                        Ver reservaciones
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
    primary: { background: '#2563eb', color: '#fff' },
    secondary: { background: '#eef2ff', color: '#1e3a8a', border: '1px solid #c7d2fe' }
};