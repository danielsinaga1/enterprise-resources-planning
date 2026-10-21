import React from "react";
import { motion } from "framer-motion";
import Backdrop from "./Backdrop";
import Button from "../Button";

const dropIn = {
  hidden: {
    y: "-100vh",
    opacity: 0,
  },
  visible: {
    y: "0",
    opacity: 1,
    transition: {
      duration: 0.1,
      type: "spring",
      damping: 25,
      stiffness: 500,
    },
  },
  exit: {
    y: "100vh",
    opacity: 0,
  },
};

interface ModalComponentProps {
  showModal: boolean;
  setShowModal: React.Dispatch<React.SetStateAction<boolean>>;
  children: React.ReactNode;
}

const Modal: React.FC<ModalComponentProps> = ({
  showModal,
  setShowModal,
  children
}) => {
  return showModal ? (
    <Backdrop onClick={() => setShowModal(false)}>
      <motion.div
        onClick={(e) => e.stopPropagation()}
        className="m-auto w-[90%] h-fit min-h-[30rem] md:w-[60%] md:h-[50%] flex items-center justify-center rounded-xl bg-primary"
        variants={dropIn}
        initial="hidden"
        animate="visible"
        exit="exit"
      >
        <div className="flex flex-col w-[60%] h-full item-center justify-center text-start child:mb-2">
          {children}
          <Button variant="secondary" onClick={() => setShowModal(false)}>
            Close
          </Button>
        </div>
      </motion.div>
    </Backdrop>
  ) : null;
};

export default Modal;
