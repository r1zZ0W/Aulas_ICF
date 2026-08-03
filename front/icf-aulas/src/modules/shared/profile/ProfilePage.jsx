import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Check, Mail, BadgeInfo, Sun, Moon } from 'lucide-react';

import { useAuth } from '../../../context/AuthContext';
import { useTheme } from '../../../context/ThemeContext';
import { ROLES, roleBadgeVariant, roleLabel } from '../../../utils/roles';
import { getInitials } from '../../../utils/format';
import { getUserSelfEditFormSchema } from '../../../schemas/user/userSelfEditForm.js';
import { getMyProfile, updateMyProfile } from '../../../api/users.js';
import { useApiMutation } from '../../../hooks/useApiMutation.js';
import { useZodForm } from '../../../hooks/useZodForm.js';

import Card from '../../../components/Card/Card';
import Badge from '../../../components/Badge/Badge';
import Button from '../../../components/Button/Button';
import Input from '../../../components/Input/Input';
import LoadingState from '../../../components/LoadingState/LoadingState';
import LoadingOverlay from '../../../components/LoadingOverlay/LoadingOverlay';
import ErrorBanner from '../../../components/ErrorBanner/ErrorBanner';

import './ProfilePage.css';

function buildInitialForm(profile) {
  return {
    firstName: profile.firstName ?? '',
    lastNames: profile.lastNames ?? '',
    username: profile.username ?? '',
    email: profile.email ?? '',
    extension: profile.extension ?? '',
    password: '',
    confirmPassword: '',
  };
}

export default function ProfilePage() {
  const { data: profile, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['profile'],
    queryFn: getMyProfile,
    staleTime: 5 * 60 * 1000,
  });

  if (isLoading) {
    return <LoadingState message="Cargando mi perfil..." />;
  }

  return (
    <div className="profile-page">
      <div className="profile-page__header">
        <div>
          <h1 className="profile-page__title">Mi Perfil</h1>
          <p className="profile-page__subtitle">Consulta y actualiza tu información de cuenta</p>
        </div>
      </div>

      {isError && (
        <ErrorBanner
          message={error?.message || 'No se pudo cargar tu perfil.'}
          onDismiss={() => refetch()}
        />
      )}

      {!isError && (
        profile ? <ProfileEditor key={profile.uuid} profile={profile} /> : null
      )}
    </div>
  );
}

function ProfileEditor({ profile }) {
  const { user, updateSession } = useAuth();
  const { theme, setTheme } = useTheme();
  const isAdmin = user.role === ROLES.ADMIN;

  // Memoized: useZodForm keys its internal useCallbacks on schema identity — a fresh instance
  // every render would reset/re-evaluate validation in a loop (see useSemestersForm.js for
  // the same pattern with getSemesterSchema).
  const schema = useMemo(() => getUserSelfEditFormSchema({ isAdmin }), [isAdmin]);
  const zod = useZodForm(buildInitialForm(profile), schema);
  const form = zod.formData;

  const saveMutation = useApiMutation({
    mutationFn: updateMyProfile,
    invalidateKey: ['profile'],
    successMessage: 'Perfil actualizado correctamente.',
    onSuccess: (updatedProfile) => {
      updateSession({
        name: `${updatedProfile.firstName} ${updatedProfile.lastNames}`.trim(),
        email: updatedProfile.email,
      });
      zod.reset(buildInitialForm(updatedProfile));
    },
  });

  const fullName = `${profile.firstName} ${profile.lastNames}`.trim();
  const avatarInitials = getInitials(profile);

  async function handleSubmit(e) {
    e.preventDefault();
    // validateAll() marks every field as touched and returns false if Zod fails — this
    // illuminates all required-field errors even on a direct "Guardar" click.
    const isValid = zod.validateAll();
    if (!isValid) return;

    const payload = {
      firstName: form.firstName.trim(),
      lastNames: form.lastNames.trim(),
      username: form.username.trim(),
      email: form.email.trim(),
      extension: form.extension.trim() || null,
      password: isAdmin && form.password ? form.password : null,
    };

    try {
      await saveMutation.mutateAsync(payload);
    } catch (err) {
      // toast handling is centralized in useApiMutation; additionally highlight the
      // offending input if the server told us which field caused it.
      if (err.fieldErrors) zod.setServerErrors(err.fieldErrors);
    }
  }

  return (
    <>
      {saveMutation.isPending && <LoadingOverlay label="Guardando cambios..." />}

      <Card className="profile-page__summary-card">
        <div className="profile-page__summary">
          <div className="profile-page__avatar" aria-hidden="true">
            {avatarInitials}
          </div>

          <div className="profile-page__summary-main">
            <div className="profile-page__summary-row">
              <h2 className="profile-page__name">{fullName}</h2>
              <div className="profile-page__badges">
                <Badge variant={roleBadgeVariant(profile.roleName ?? user.role)}>
                  {roleLabel(profile.roleName ?? user.role)}
                </Badge>
                <Badge variant="neutral">
                  {profile.institutionalId || 'Sin matrícula'}
                </Badge>
              </div>
            </div>

            <p className="profile-page__email">
              <Mail size={15} />
              <span>{profile.email ?? user.email}</span>
            </p>

            <p className="profile-page__note">
              Gestiona tu información personal y de acceso
            </p>
          </div>
        </div>
      </Card>

      <Card className="profile-page__form-card">
        <div className="profile-page__section-head">
          <div>
            <h3 className="profile-page__section-title">Información Personal</h3>
          </div>
          <span className="profile-page__section-note">Los campos con * son obligatorios</span>
        </div>

        <form onSubmit={handleSubmit} className="profile-page__form">
          <div className="profile-page__grid">
            <Input
              label="Nombre completo *"
              value={form.firstName}
              onChange={(e) => zod.handleChange('firstName', e.target.value)}
              onBlur={() => zod.handleBlur('firstName')}
              placeholder="Ingresa tu nombre completo"
              error={zod.errors.firstName}
              required
            />

            <Input
              label="Apellidos *"
              value={form.lastNames}
              onChange={(e) => zod.handleChange('lastNames', e.target.value)}
              onBlur={() => zod.handleBlur('lastNames')}
              placeholder="Ingresa tus apellidos"
              error={zod.errors.lastNames}
              required
            />

            <Input
              label="Nombre de usuario *"
              value={form.username}
              onChange={(e) => zod.handleChange('username', e.target.value)}
              onBlur={() => zod.handleBlur('username')}
              placeholder="Ingresa un nombre de usuario"
              error={zod.errors.username}
              required
            />

            <Input
              label="Correo electrónico *"
              type="email"
              value={form.email}
              onChange={(e) => zod.handleChange('email', e.target.value)}
              onBlur={() => zod.handleBlur('email')}
              placeholder="correo@icf.unam.mx"
              error={zod.errors.email}
              required
            />

            <Input
              label="Extensión"
              value={form.extension}
              onChange={(e) => zod.handleChange('extension', e.target.value)}
              onBlur={() => zod.handleBlur('extension')}
              placeholder="Extensión interna"
              error={zod.errors.extension}
            />

            <div className="profile-page__access-block profile-page__grid--full">
              <div className="profile-page__access-head">
                <div>
                  <h4 className="profile-page__access-title">Contraseña</h4>
                  <p className="profile-page__access-text">
                    {isAdmin
                      ? 'Como administrador puedes cambiar tu contraseña desde aquí.'
                      : 'Los maestros no pueden cambiar su contraseña desde este perfil.'}
                  </p>
                </div>
                <Badge variant={isAdmin ? 'primary' : 'neutral'}>
                  <BadgeInfo size={12} />
                  <span>{isAdmin ? 'Administrador' : 'Maestro'}</span>
                </Badge>
              </div>

              {isAdmin && (
                <div className="profile-page__password-grid">
                  <Input
                    label="Nueva contraseña"
                    type="password"
                    value={form.password}
                    onChange={(e) => zod.handleChange('password', e.target.value)}
                    onBlur={() => zod.handleBlur('password')}
                    placeholder="Ingresa tu nueva contraseña"
                    error={zod.errors.password}
                  />
                  <Input
                    label="Confirmar contraseña"
                    type="password"
                    value={form.confirmPassword}
                    onChange={(e) => zod.handleChange('confirmPassword', e.target.value)}
                    onBlur={() => zod.handleBlur('confirmPassword')}
                    placeholder="Repite la nueva contraseña"
                    error={zod.errors.confirmPassword}
                  />
                </div>
              )}
            </div>
          </div>

          <div className="profile-page__actions">
            <Button
              type="submit"
              variant="primary"
              size="medium"
              fullWidth
              iconLeft={<Check size={18} />}
            >
              Guardar cambios
            </Button>
          </div>
        </form>
      </Card>

      <Card className="profile-page__theme-card">
        <div className="profile-page__section-head">
          <div>
            <h3 className="profile-page__section-title">Apariencia</h3>
          </div>
          <span className="profile-page__section-note">Personaliza el aspecto de la interfaz</span>
        </div>

        <div className="profile-page__theme-options">
          <div className="profile-page__theme-info">
            <h4 className="profile-page__theme-title">Modo de la página</h4>
            <p className="profile-page__theme-desc">
              Selecciona tu tema preferido para la aplicación.
            </p>
          </div>

          <div className="profile-page__theme-selector">
            <button
              type="button"
              className={`profile-page__theme-btn ${theme === 'light' ? 'profile-page__theme-btn--active' : ''}`}
              onClick={() => setTheme('light')}
              aria-label="Modo Claro"
            >
              <Sun size={18} />
              <span>Modo Claro</span>
            </button>

            <button
              type="button"
              className={`profile-page__theme-btn ${theme === 'dark' ? 'profile-page__theme-btn--active' : ''}`}
              onClick={() => setTheme('dark')}
              aria-label="Modo Oscuro"
            >
              <Moon size={18} />
              <span>Modo Oscuro</span>
            </button>
          </div>
        </div>
      </Card>
    </>
  );
}
