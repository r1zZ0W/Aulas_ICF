import { toast as sonnerToast } from "sonner";
import ToastWithProgress from "../components/ToastWithProgress/ToastWithProgress";

const DURATION = 3000;

export function toast(message, type = "success", duration = DURATION) {
  return sonnerToast.custom(
    () => <ToastWithProgress message={message} type={type} duration={duration} />,
    { duration }
  );
}

toast.success = (message, duration = DURATION) => {
  return toast(message, "success", duration);
};

toast.error = (message, duration = DURATION) => {
  return toast(message, "error", duration);
};

toast.info = (message, duration = DURATION) => {
  return toast(message, "info", duration);
};

toast.dismiss = (id) => {
  sonnerToast.dismiss(id);
};
