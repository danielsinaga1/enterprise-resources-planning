import React, { ReactNode } from "react";

interface ButtonProps {
  variant: "primary" | "secondary" | "increment" | "category";
  name?: string;
  onClick?: () => void;
  children?: string | ReactNode;
}

const Button: React.FC<ButtonProps> = ({ variant, name, onClick, children }) => {
  const baseClasses = "px-10 py-3 rounded text-center";

  const variantClasses = {
    primary: "bg-primary text-secondary hover:bg-gray-100",
    secondary: "bg-secondary text-primary hover:bg-gray-600",
    increment: "text-gray-600",
    category: "text-secondary text-start",
  };
  return (
    <button
      name={name}
      onClick={onClick}
      className={`${baseClasses} ${variantClasses[variant]}`}
    >
      {children}
    </button>
  );
};

export default Button;
