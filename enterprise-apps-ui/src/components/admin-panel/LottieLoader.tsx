import Lottie from "lottie-web";
import React, { useEffect, useRef } from "react";

interface LottieLoaderProps {
  path?: string;
  width?: number;
  height?: number;
}

const LottieLoader: React.FC<LottieLoaderProps> = ({
  path,
  width = 200,
  height = 200,
}) => {
  const lottieRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (lottieRef.current) {
      const animation = Lottie.loadAnimation({
        container: lottieRef.current,
        renderer: 'svg',
        loop: true,
        autoplay: true,
        path: path,
      });

      return () => animation.destroy();
    }
  }, [path]);

  return <div ref={lottieRef} style={{ width, height }}></div>;
};

export default LottieLoader;
