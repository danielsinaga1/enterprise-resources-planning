import React from "react";
import { Link } from "react-router-dom";
import Button from "./Button";

interface FeaturedProductsProps {
  title: string;
  subTitle: string;
  children: React.ReactNode;
}

const FeaturedProducts: React.FC<FeaturedProductsProps> = ({
  title,
  subTitle,
  children,
}) => {
  return (
    <div className="w-full h-fit py-8 flex flex-col items-start">
      <h3>{title}</h3>
      <h4>{subTitle}</h4>
      <div className="grid w-full mt-7 grid-cols-1 sm:grid-cols-3 md:grid-cols-5 gap-3">
        {children}
        <div className="flex flex-col items-start justify-center pl-3 pb-20">
          <h3>Explore other</h3>
          <Link to="/products">
            <Button variant="secondary">Shop Now</Button>
          </Link>
        </div>
      </div>
    </div>
  );
};

export default FeaturedProducts;
