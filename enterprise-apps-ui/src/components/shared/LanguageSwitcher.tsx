import { useTranslation } from 'react-i18next';
import { Button } from 'primereact/button';

const LanguageSwitcher = () => {
  const { i18n } = useTranslation();

  const toggle = () => {
    const next = i18n.language === 'id' ? 'en' : 'id';
    i18n.changeLanguage(next);
    localStorage.setItem('ea-lang', next);
  };

  return (
    <Button
      label={i18n.language === 'id' ? 'EN' : 'ID'}
      icon="pi pi-globe"
      text
      rounded
      size="small"
      onClick={toggle}
      tooltip={i18n.language === 'id' ? 'Switch to English' : 'Ganti ke Bahasa Indonesia'}
      tooltipOptions={{ position: 'bottom' }}
      className="text-xs"
    />
  );
};

export default LanguageSwitcher;
