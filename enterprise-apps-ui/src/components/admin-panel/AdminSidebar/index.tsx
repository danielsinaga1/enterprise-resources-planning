import React, { useEffect, useRef, useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';

interface AdminSidebarProps {
  sidebarOpen: boolean;
  setSidebarOpen: (arg: boolean) => void;
}

interface SubMenuItem {
  title: string;
  path: string;
}

interface MenuItem {
  title: string;
  path?: string;
  icon: React.ReactNode;
  submenu?: SubMenuItem[];
}

interface MenuSection {
  sectionTitle: string;
  items: MenuItem[];
}

const AdminSidebar = ({ sidebarOpen, setSidebarOpen }: AdminSidebarProps) => {
  const location = useLocation();
  const { pathname } = location;

  const trigger = useRef<HTMLButtonElement>(null);
  const sidebar = useRef<HTMLElement>(null);

  const [openSubmenu, setOpenSubmenu] = useState<string | null>(null);

  const storedSidebarExpanded = localStorage.getItem('sidebar-expanded');
  const [sidebarExpanded] = useState(
    storedSidebarExpanded === null ? false : storedSidebarExpanded === 'true'
  );

  // close on click outside
  useEffect(() => {
    const clickHandler = ({ target }: MouseEvent) => {
      if (!sidebar.current || !trigger.current) return;
      if (
        !sidebarOpen ||
        sidebar.current.contains(target as Node) ||
        trigger.current.contains(target as Node)
      )
        return;
      setSidebarOpen(false);
    };
    document.addEventListener('click', clickHandler);
    return () => document.removeEventListener('click', clickHandler);
  });

  // close if the esc key is pressed
  useEffect(() => {
    const keyHandler = ({ keyCode }: KeyboardEvent) => {
      if (!sidebarOpen || keyCode !== 27) return;
      setSidebarOpen(false);
    };
    document.addEventListener('keydown', keyHandler);
    return () => document.removeEventListener('keydown', keyHandler);
  });

  useEffect(() => {
    localStorage.setItem('sidebar-expanded', sidebarExpanded.toString());
    if (sidebarExpanded) {
      document.querySelector('body')?.classList.add('sidebar-expanded');
    } else {
      document.querySelector('body')?.classList.remove('sidebar-expanded');
    }
  }, [sidebarExpanded]);

  const toggleSubmenu = (title: string) => {
    setOpenSubmenu(openSubmenu === title ? null : title);
  };

  const menuSections: MenuSection[] = [
    {
      sectionTitle: 'MAIN',
      items: [
        {
          title: 'Dashboard',
          path: '/dashboard',
          icon: (
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 5a1 1 0 011-1h14a1 1 0 011 1v2a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM4 13a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H5a1 1 0 01-1-1v-6zM16 13a1 1 0 011-1h2a1 1 0 011 1v6a1 1 0 01-1 1h-2a1 1 0 01-1-1v-6z" />
            </svg>
          ),
        },
      ],
    },
    {
      sectionTitle: 'PENGATURAN',
      items: [
        {
          title: 'Manajemen User',
          icon: (
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
            </svg>
          ),
          submenu: [
            { title: 'Daftar User', path: '/dashboard/settings/users' },
            { title: 'Role & Permission', path: '/dashboard/settings/roles' },
          ],
        },
        {
          title: 'Sistem',
          icon: (
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          ),
          submenu: [
            { title: 'Pengaturan Umum', path: '/dashboard/settings/general' },
            { title: 'Audit Log', path: '/dashboard/settings/audit-log' },
            { title: 'Backup Data', path: '/dashboard/settings/backup' },
          ],
        },
      ],
    },
  ];

  // Auto-open submenu that contains the active route on mount and path changes
  useEffect(() => {
    for (const section of menuSections) {
      for (const item of section.items) {
        if (item.submenu?.some(sub => pathname === sub.path || pathname.startsWith(sub.path + '/'))) {
          setOpenSubmenu(item.title);
          return;
        }
      }
    }
  }, [pathname]);

  return (
    <aside
      ref={sidebar}
      className={`absolute left-0 top-0 z-9999 flex h-screen w-72 flex-col overflow-y-hidden bg-gradient-to-b from-gray-900 via-gray-900 to-gray-800 duration-300 ease-linear lg:static lg:translate-x-0 ${
        sidebarOpen ? 'translate-x-0' : '-translate-x-full'
      }`}
    >
      {/* SIDEBAR HEADER */}
      <div className="flex items-center justify-between gap-2 px-6 py-5 border-b border-gray-700/50">
        <NavLink to="/dashboard" className="flex items-center gap-3">
          <div className="flex items-center justify-center rounded-lg w-9 h-9 bg-gradient-to-br from-indigo-400 to-indigo-600">
            <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
            </svg>
          </div>
          <div className="flex flex-col">
            <span className="text-white font-bold text-base tracking-wide">IRMS</span>
            <span className="text-gray-400 text-xs">Hospital System</span>
          </div>
        </NavLink>

        <button
          ref={trigger}
          onClick={() => setSidebarOpen(!sidebarOpen)}
          aria-controls="sidebar"
          aria-expanded={sidebarOpen}
          className="block lg:hidden p-1.5 rounded-lg hover:bg-gray-700/50 transition-colors"
        >
          <svg className="w-6 h-6 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <div className="no-scrollbar flex flex-col overflow-y-auto duration-300 ease-linear">
        {/* Sidebar Menu */}
        <nav className="py-4 px-3">
          {menuSections.map((section, sectionIndex) => (
            <div key={sectionIndex} className="mb-6">
              <h3 className="mb-3 px-4 text-xs font-semibold uppercase tracking-wider text-gray-500">
                {section.sectionTitle}
              </h3>
              <ul className="flex flex-col gap-1">
                {section.items.map((item, itemIndex) => (
                  <li key={itemIndex}>
                    {item.submenu ? (
                      <div>
                        <button
                          onClick={() => toggleSubmenu(item.title)}
                          className={`w-full group relative flex items-center justify-between gap-3 rounded-lg px-4 py-2.5 font-medium transition-all duration-200 ${
                            openSubmenu === item.title || item.submenu.some(sub => pathname === sub.path)
                              ? 'bg-gray-700/50 text-white'
                              : 'text-gray-400 hover:bg-gray-700/30 hover:text-white'
                          }`}
                        >
                          <div className="flex items-center gap-3">
                            {item.icon}
                            <span className="text-sm">{item.title}</span>
                          </div>
                          <svg
                            className={`w-4 h-4 transition-transform duration-200 ${
                              openSubmenu === item.title ? 'rotate-180' : ''
                            }`}
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                          >
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                          </svg>
                        </button>
                        <div
                          className={`overflow-hidden transition-all duration-300 ${
                            openSubmenu === item.title ? 'max-h-96 opacity-100' : 'max-h-0 opacity-0'
                          }`}
                        >
                          <ul className="mt-1 ml-4 pl-4 border-l border-gray-700/50">
                            {item.submenu.map((subItem, subIndex) => (
                              <li key={subIndex}>
                                <NavLink
                                  to={subItem.path}
                                  className={({ isActive }) =>
                                    `block py-2 px-3 text-sm rounded-lg transition-all duration-200 ${
                                      isActive
                                        ? 'text-primary font-medium bg-primary/10'
                                        : 'text-gray-400 hover:text-white hover:bg-gray-700/30'
                                    }`
                                  }
                                >
                                  {subItem.title}
                                </NavLink>
                              </li>
                            ))}
                          </ul>
                        </div>
                      </div>
                    ) : (
                      <NavLink
                        to={item.path!}
                        className={({ isActive }) =>
                          `group relative flex items-center gap-3 rounded-lg px-4 py-2.5 font-medium transition-all duration-200 ${
                            isActive
                              ? 'bg-primary text-white shadow-lg shadow-primary/30'
                              : 'text-gray-400 hover:bg-gray-700/30 hover:text-white'
                          }`
                        }
                      >
                        {item.icon}
                        <span className="text-sm">{item.title}</span>
                        {pathname === item.path && (
                          <span className="absolute right-3 w-2 h-2 rounded-full bg-white animate-pulse" />
                        )}
                      </NavLink>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>
      </div>
    </aside>
  );
};

export default AdminSidebar;
