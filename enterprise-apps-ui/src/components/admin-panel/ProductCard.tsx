import React from "react";
import { Link } from "react-router-dom";
import { Product } from "./types/product";

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8005';

const ProductCard: React.FC<{ product: Product }> = ({ product }) => {
  // Get first category name for display
  const categoryName = product.categories && product.categories.length > 0 
    ? product.categories[0].name 
    : 'Product';
  
  return (
    <Link to={`/product/${product.idProduct}`}>
      <div className="group bg-white dark:bg-boxdark rounded-2xl overflow-hidden shadow-md hover:shadow-2xl transition-all duration-500 hover:-translate-y-2">
        {/* Image Container */}
        <div className="relative h-64 bg-gradient-to-br from-gray-100 to-gray-50 dark:from-boxdark-2 dark:to-boxdark overflow-hidden">
          {product.image ? (
            <img 
              src={`${API_BASE_URL}${product.image}`} 
              alt={product.title}
              className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-500"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center p-4">
              <div className="w-24 h-24 bg-primary/10 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform duration-500">
                <svg className="w-12 h-12 text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                </svg>
              </div>
            </div>
          )}
          
          {/* Badge */}
          <div className="absolute top-4 left-4">
            <span className="px-3 py-1 bg-primary text-white text-xs font-semibold rounded-full">
              {categoryName}
            </span>
          </div>
          
          {/* Quick Actions */}
          <div className="absolute top-4 right-4 flex flex-col gap-2 opacity-0 group-hover:opacity-100 transition-opacity duration-300">
            <button className="w-10 h-10 bg-white dark:bg-boxdark rounded-full shadow-lg flex items-center justify-center hover:bg-primary hover:text-white transition-colors duration-300">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
              </svg>
            </button>
            <button className="w-10 h-10 bg-white dark:bg-boxdark rounded-full shadow-lg flex items-center justify-center hover:bg-primary hover:text-white transition-colors duration-300">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
              </svg>
            </button>
          </div>
        </div>
        
        {/* Content */}
        <div className="p-5">
          {/* Markets tags */}
          {product.markets && product.markets.length > 0 && (
            <div className="flex items-center gap-2 mb-2 flex-wrap">
              {product.markets.slice(0, 2).map(market => (
                <span key={market.id} className="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wider bg-gray-100 dark:bg-gray-700 px-2 py-0.5 rounded">
                  {market.name}
                </span>
              ))}
            </div>
          )}
          
          <h3 className="font-bold text-gray-900 dark:text-white text-lg mb-2 group-hover:text-primary transition-colors duration-300 line-clamp-2">
            {product.title}
          </h3>
          
          {/* Rating */}
          <div className="flex items-center gap-1 mb-3">
            {[...Array(5)].map((_, i) => (
              <svg key={i} className="w-4 h-4 text-yellow-400" fill="currentColor" viewBox="0 0 20 20">
                <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
              </svg>
            ))}
            <span className="text-sm text-gray-500 dark:text-gray-400 ml-1">(4.9)</span>
          </div>
          
          {/* Functions tags */}
          {product.functions && product.functions.length > 0 && (
            <div className="flex items-center gap-2 mb-4 flex-wrap">
              {product.functions.slice(0, 3).map((func) => (
                <span
                  key={func.id}
                  className="px-2 py-1 bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400 text-xs rounded-full"
                >
                  {func.name}
                </span>
              ))}
            </div>
          )}
          
          {/* Points & View Details */}
          <div className="flex items-center justify-between">
            <div className="flex flex-col">
              <span className="text-2xl font-bold text-primary">
                {product.poin || 0} pts
              </span>
            </div>
            <button className="px-4 py-2 bg-primary text-white rounded-lg font-semibold hover:bg-primary/90 transition-all duration-300 text-sm flex items-center gap-2">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
              </svg>
              View
            </button>
          </div>
        </div>
      </div>
    </Link>
  );
};

export default ProductCard;
