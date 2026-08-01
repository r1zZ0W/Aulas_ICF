import { createContext, useContext, useState, useEffect } from 'react';

const STORAGE_KEY = 'app-theme';
const ThemeContext = createContext();

export function ThemeProvider({ children }) {
  const [theme, setThemeState] = useState(() => {
    // A synchronous script in index.html already set document.documentElement.dataset.theme
    // before first paint (reading localStorage, falling back to prefers-color-scheme) to
    // avoid a flash-of-wrong-theme on hard reload. Read that back first so React's initial
    // render agrees with what's already on screen instead of overriding it a tick later.
    const paintedTheme = document.documentElement.dataset.theme;
    if (paintedTheme === 'dark' || paintedTheme === 'light') {
      return paintedTheme;
    }
    try {
      const savedTheme = localStorage.getItem(STORAGE_KEY);
      if (savedTheme === 'dark' || savedTheme === 'light') {
        return savedTheme;
      }
      if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
        return 'dark';
      }
    } catch {
      // Ignorar errores de acceso a localStorage
    }
    return 'light'; // Último recurso si no hay tema pintado, guardado ni preferencia del SO
  });

  useEffect(() => {
    // Keeps data-theme in sync on every change after mount (toggle, explicit setTheme).
    // The *initial* value is already painted by the inline script in index.html before
    // React even runs; this only re-applies it when `theme` changes afterwards.
    const root = document.documentElement;
    root.setAttribute('data-theme', theme);
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Ignorar errores de localStorage
    }
  }, [theme]);

  const setTheme = (newTheme) => {
    if (newTheme === 'dark' || newTheme === 'light') {
      setThemeState(newTheme);
    }
  };

  const toggleTheme = () => {
    setThemeState((prevTheme) => (prevTheme === 'dark' ? 'light' : 'dark'));
  };

  return (
    <ThemeContext.Provider
      value={{
        theme,
        setTheme,
        toggleTheme,
        isDark: theme === 'dark',
      }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme debe ser usado dentro de un ThemeProvider');
  }
  return context;
}
