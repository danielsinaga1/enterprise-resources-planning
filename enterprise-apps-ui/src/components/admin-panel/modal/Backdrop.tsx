import { motion } from "framer-motion";
import React, { MouseEventHandler, ReactNode } from "react";

interface BackdropProps {
  children: ReactNode;
  onClick: MouseEventHandler<HTMLDivElement>;
}

const backdropStyle: React.CSSProperties = {
  position: "fixed",
  top: 0,
  left: 0,
  width: "100%",
  height: "100%",
  backgroundColor: "rgba(0, 0, 0, 0.5)",
  zIndex: 100,
  display: "flex",
};

const Backdrop: React.FC<BackdropProps> = ({ children, onClick }) => {
  return (
    <motion.div
      onClick={onClick}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      style={backdropStyle}
    >
      {children}
    </motion.div>
  );
};

export default Backdrop;
