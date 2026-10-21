import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from 'primereact/button';
import { InputText } from 'primereact/inputtext';
import { Card } from 'primereact/card';
import { BreadCrumb } from 'primereact/breadcrumb';
import { Avatar } from 'primereact/avatar';
import { Message } from 'primereact/message';
import { Tag } from 'primereact/tag';
import { ProgressSpinner } from 'primereact/progressspinner';
import { useAuth } from '../../../context/AuthContext';
import { authService } from '../../../services/authService';
import type { ProfileResponse } from '../../../types';

const ProfilePage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();

  // Profile state
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [name, setName] = useState('');
  const [mobilePhone, setMobilePhone] = useState('');
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileMsg, setProfileMsg] = useState<{ severity: 'success' | 'error'; text: string } | null>(null);

  const breadcrumbHome = { icon: 'pi pi-home', command: () => navigate('/dashboard') };
  const breadcrumbItems = [{ label: 'Profil Saya' }];

  useEffect(() => {
    loadProfile();
  }, []);

  const loadProfile = async () => {
    if (!user?.id) return;
    setLoading(true);
    try {
      const data = await authService.getProfile(user.id);
      if (data.success) {
        setProfile(data);
        setName(data.name || '');
        setMobilePhone(data.mobilePhone || '');
      }
    } catch {
      setProfileMsg({ severity: 'error', text: 'Gagal memuat data profil' });
    } finally {
      setLoading(false);
    }
  };

  const handleSaveProfile = async () => {
    if (!user?.id) return;
    setSavingProfile(true);
    setProfileMsg(null);
    try {
      const data = await authService.updateProfile(user.id, { name, mobilePhone });
      if (data.success) {
        setProfile(data);
        setProfileMsg({ severity: 'success', text: 'Profil berhasil diperbarui' });
        // Update user in localStorage
        const storedUser = authService.getUser();
        if (storedUser) {
          storedUser.name = data.name;
          storedUser.mobilePhone = data.mobilePhone;
          localStorage.setItem('user', JSON.stringify(storedUser));
        }
      } else {
        setProfileMsg({ severity: 'error', text: data.message || 'Gagal memperbarui profil' });
      }
    } catch {
      setProfileMsg({ severity: 'error', text: 'Terjadi kesalahan saat memperbarui profil' });
    } finally {
      setSavingProfile(false);
      setTimeout(() => setProfileMsg(null), 5000);
    }
  };

  const initials = user?.name
    ?.split(' ')
    .map((n) => n[0])
    .join('')
    .substring(0, 2)
    .toUpperCase() ?? 'U';

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 300 }}>
        <ProgressSpinner style={{ width: 50, height: 50 }} />
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <BreadCrumb model={breadcrumbItems} home={breadcrumbHome} />

      <h2 style={{ margin: 0, fontSize: '1.5rem', fontWeight: 700 }}>Profil Saya</h2>

      {/* Profile Header Card */}
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 20, flexWrap: 'wrap' }}>
          <Avatar
            label={initials}
            shape="circle"
            size="xlarge"
            style={{
              background: 'linear-gradient(135deg, #6366f1, #818cf8)',
              color: '#fff',
              fontWeight: 700,
              fontSize: '1.5rem',
              width: 80,
              height: 80,
            }}
          />
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#1e293b' }}>
              {profile?.name || user?.name || '-'}
            </div>
            <div style={{ fontSize: '0.9rem', color: '#64748b', marginTop: 4 }}>
              {profile?.email || user?.email || '-'}
            </div>
            <div style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
              {user?.roles?.map((role) => (
                <Tag key={role} value={role} severity="info" style={{ textTransform: 'capitalize' }} />
              ))}
            </div>
          </div>
          <div style={{ textAlign: 'right', color: '#64748b', fontSize: '0.85rem' }}>
            <div>
              <i className="pi pi-id-card" style={{ marginRight: 6 }} />
              NPK: {profile?.npk || user?.npk || '-'}
            </div>
            {profile?.lastLogin && (
              <div style={{ marginTop: 4 }}>
                <i className="pi pi-clock" style={{ marginRight: 6 }} />
                Login terakhir: {new Date(profile.lastLogin).toLocaleString('id-ID')}
              </div>
            )}
          </div>
        </div>
      </Card>

      {/* Edit Profile Card */}
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
          <i className="pi pi-user-edit" style={{ fontSize: '1.3rem', color: '#3B82F6' }} />
          <span style={{ fontSize: '1.1rem', fontWeight: 700 }}>Informasi Profil</span>
        </div>

        {profileMsg && (
          <Message severity={profileMsg.severity} text={profileMsg.text} style={{ width: '100%', marginBottom: 16 }} />
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            <div style={{ flex: '1 1 250px' }}>
              <label style={{ display: 'block', marginBottom: 6, fontWeight: 600, fontSize: '0.9rem' }}>NPK</label>
              <InputText value={profile?.npk || user?.npk || ''} disabled style={{ width: '100%', background: '#f1f5f9' }} />
              <small style={{ color: '#94a3b8' }}>NPK tidak dapat diubah</small>
            </div>
            <div style={{ flex: '1 1 250px' }}>
              <label style={{ display: 'block', marginBottom: 6, fontWeight: 600, fontSize: '0.9rem' }}>Email</label>
              <InputText value={profile?.email || ''} disabled style={{ width: '100%', background: '#f1f5f9' }} />
              <small style={{ color: '#94a3b8' }}>Email tidak dapat diubah dari halaman ini</small>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            <div style={{ flex: '1 1 250px' }}>
              <label style={{ display: 'block', marginBottom: 6, fontWeight: 600, fontSize: '0.9rem' }}>
                Nama Lengkap <span style={{ color: '#ef4444' }}>*</span>
              </label>
              <InputText
                value={name}
                onChange={(e) => setName(e.target.value)}
                style={{ width: '100%' }}
                placeholder="Masukkan nama lengkap"
              />
            </div>
            <div style={{ flex: '1 1 250px' }}>
              <label style={{ display: 'block', marginBottom: 6, fontWeight: 600, fontSize: '0.9rem' }}>
                No. Telepon
              </label>
              <InputText
                value={mobilePhone}
                onChange={(e) => setMobilePhone(e.target.value)}
                style={{ width: '100%' }}
                placeholder="Masukkan nomor telepon"
              />
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 20 }}>
          <Button
            label="Simpan Profil"
            icon="pi pi-check"
            onClick={handleSaveProfile}
            loading={savingProfile}
            disabled={!name.trim()}
          />
        </div>
      </Card>
    </div>
  );
};

export default ProfilePage;
