import React from 'react'
import { NavLink } from 'react-router-dom';

const navItems = [
  { name: 'Home', path: '/' },
  { name: 'Products', path: '/products' },
  { name: 'Categories', path: '/products?category=all' },
  { name: 'About', path: '/about' },
  { name: 'Contact', path: '/contact' },
];

const NavBar: React.FC = () => {
  return (
    <nav className='flex items-center space-x-8'>
      {navItems.map((item) => (
        <NavLink
          key={item.name}
          to={item.path}
          className={({ isActive }) =>
            `text-sm font-medium transition-colors duration-200 ${
              isActive 
                ? 'text-primary' 
                : 'text-gray-700 dark:text-gray-200 hover:text-primary'
            }`
          }
        >
          {item.name}
        </NavLink>
      ))}
    </nav>
  )
}

export default NavBar