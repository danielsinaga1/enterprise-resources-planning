import React, { useState } from "react";
import { NavLink, Link } from "react-router-dom";
import NavBar from "./components/NavBar";
import BurgerMenu from "./components/BurgerMenu";
import { useAuth } from "../../context/authContext";
import { useCart } from "../../context/cartContext";

const HeaderComponent: React.FC = () => {
  const { user, logout } = useAuth();
  const { getTotalItems } = useCart();
  const [isScrolled, setIsScrolled] = useState(false);
  
  const cartItemCount = getTotalItems();

  React.useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 10);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <header className={`w-full sticky top-0 z-50 transition-all duration-300 ${
      isScrolled 
        ? 'bg-white dark:bg-boxdark shadow-md' 
        : 'bg-white dark:bg-boxdark'
    }`}>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16 lg:h-20">
          {/* Mobile Menu Button */}
          <div className="lg:hidden">
            <BurgerMenu />
          </div>

          {/* Logo */}
          <NavLink to="/" className="flex-shrink-0">
            <img
              src="/assets/Logo-T.png"
              alt="ThreeBond Logo"
              className="h-10 lg:h-12 w-auto"
            />
          </NavLink>

          {/* Desktop Navigation */}
          <div className="hidden lg:flex flex-1 justify-center">
            <NavBar />
          </div>

          {/* Right Side - Icons & Auth */}
          <div className="flex items-center space-x-4">
            {/* User Icon */}
            <Link to={user ? "/profile" : "/login"} className="text-gray-700 dark:text-gray-200 hover:text-primary transition-colors">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
              </svg>
            </Link>

            {/* My Points Icon - Only show when logged in */}
            {user && (
              <Link to="/my-points" className="text-gray-700 dark:text-gray-200 hover:text-primary transition-colors" title="My Points">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </Link>
            )}
            
            {/* Wishlist Icon */}
            <Link to="/wishlist" className="text-gray-700 dark:text-gray-200 hover:text-primary transition-colors">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
              </svg>
            </Link>

            {/* Cart Icon */}
            <Link to="/cart" className="text-gray-700 dark:text-gray-200 hover:text-primary transition-colors relative">
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
              </svg>
              {cartItemCount > 0 && (
                <span className="absolute -top-2 -right-2 bg-primary text-white text-xs w-5 h-5 rounded-full flex items-center justify-center">
                  {cartItemCount > 99 ? '99+' : cartItemCount}
                </span>
              )}
            </Link>

            {/* Auth */}
            {user ? (
              <div className="hidden sm:flex items-center space-x-3">
                {user.role === 'admin' && (
                  <Link 
                    to="/admin" 
                    className="text-sm font-medium text-primary hover:text-primary/80 transition-colors"
                  >
                    Admin
                  </Link>
                )}
                <button 
                  onClick={logout}
                  className="text-sm font-medium text-gray-700 dark:text-gray-200 hover:text-primary transition-colors"
                >
                  Logout
                </button>
              </div>
            ) : (
              <Link 
                to="/login" 
                className="hidden sm:inline-flex px-4 py-2 bg-primary text-white text-sm font-semibold rounded-lg hover:bg-primary/90 transition-colors"
              >
                Login
              </Link>
            )}
          </div>
        </div>
      </div>
    </header>
  );
};

export default HeaderComponent;
