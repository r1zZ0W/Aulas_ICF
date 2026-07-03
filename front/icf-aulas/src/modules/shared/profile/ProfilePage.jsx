import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Check, Mail, BadgeInfo } from 'lucide-react';

import { useAuth } from '../../../context/AuthContext';
import { ROLES, roleBadgeVariant, roleLabel } from '../../../utils/roles';
import { getInitials } from '../../../utils/format';
import { UserSelfEditSchema } from '../../../schemas/index.js';
import { getMyProfile, updateMyProfile } from '../../../api/users.js';
import { useApiMutation } from '../../../hooks/useApiMutation.js';

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

function flattenZodError(zodError) {
  const out = {};
  for (const issue of zodError.issues ?? []) {
    const key = issue.path[0];
    if (key && !out[key]) out[key] = issue.message;
  }
  return out;
}

export default function ProfilePage() {
  const { data: profile, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['profile'],
    queryFn: getMyProfile,
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
  const isAdmin = user.role === ROLES.ADMIN;
  const [form, setForm] = useState(() => buildInitialForm(profile));
  const [formErrors, setFormErrors] = useState({});

  const saveMutation = useApiMutation({
    mutationFn: updateMyProfile,
    invalidateKey: ['profile'],
    successMessage: 'Perfil actualizado correctamente.',
    onSuccess: (updatedProfile) => {
      updateSession({
        name: `${updatedProfile.firstName} ${updatedProfile.lastNames}`.trim(),
        email: updatedProfile.email,
      });
      setForm(buildInitialForm(updatedProfile));
      setFormErrors({});
    },
  });

  const fullName = `${profile.firstName} ${profile.lastNames}`.trim();
  const avatarInitials = getInitials(profile);

  function handleField(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
    setFormErrors((current) => ({ ...current, [field]: undefined }));
  }

  function validate() {
    const payload = {
      firstName: form.firstName.trim(),
      lastNames: form.lastNames.trim(),
      username: form.username.trim(),
      email: form.email.trim(),
      extension: form.extension.trim() || null,
      password: isAdmin && form.password.trim() ? form.password : null,
    };

    const parsed = UserSelfEditSchema.safeParse(payload);
    if (!parsed.success) {
      setFormErrors(flattenZodError(parsed.error));
      return null;
    }

    if (isAdmin && form.password.trim() && form.password !== form.confirmPassword) {
      setFormErrors((current) => ({
        ...current,
        confirmPassword: 'Las contraseñas no coinciden',
      }));
      return null;
    }

    return parsed.data;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    const payload = validate();
    if (!payload) return;

    try {
      await saveMutation.mutateAsync(payload);
    } catch {
      // toast handling is centralized in useApiMutation
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
                  {console.log(profile)}
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
              onChange={(e) => handleField('firstName', e.target.value)}
              placeholder="Ingresa tu nombre completo"
              error={formErrors.firstName}
              required
            />

            <Input
              label="Apellidos *"
              value={form.lastNames}
              onChange={(e) => handleField('lastNames', e.target.value)}
              placeholder="Ingresa tus apellidos"
              error={formErrors.lastNames}
              required
            />

            <Input
              label="Nombre de usuario *"
              value={form.username}
              onChange={(e) => handleField('username', e.target.value)}
              placeholder="Ingresa un nombre de usuario"
              error={formErrors.username}
              required
            />

            <Input
              label="Correo electrónico *"
              type="email"
              value={form.email}
              onChange={(e) => handleField('email', e.target.value)}
              placeholder="correo@icf.unam.mx"
              error={formErrors.email}
              required
            />

            <Input
              label="Extensión"
              value={form.extension}
              onChange={(e) => handleField('extension', e.target.value)}
              placeholder="Extensión interna"
              error={formErrors.extension}
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
                    onChange={(e) => handleField('password', e.target.value)}
                    placeholder="Ingresa tu nueva contraseña"
                    error={formErrors.password}
                  />
                  <Input
                    label="Confirmar contraseña"
                    type="password"
                    value={form.confirmPassword}
                    onChange={(e) => handleField('confirmPassword', e.target.value)}
                    placeholder="Repite la nueva contraseña"
                    error={formErrors.confirmPassword}
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
    </>
  );
}
