import {useLayoutEffect, useRef, useState} from 'react';

export interface ModalState {
  open: boolean;
  setOpen: (open: boolean) => void;
}

interface Props {
  title: string;
  state: ModalState;
}

/**
 * A custom hook for getting a ModalState object associated with a closed modal.
 * This can be used to control one or more modal children.
 * @returns The new modal state, closed
 */
export function useModal(): ModalState {
  const [open, setOpen] = useState(false);
  return {open, setOpen};
}

/**
 * Determines whether any modal is currently open
 */
export function anyModalOpen(): boolean {
  return document.querySelector('dialog:modal') !== null;
}

/**
 * Represents a modal dialog box, implemented on top of the native HTML <dialog> element for maximal native support.
 * @param state The state of the modal, and the ability to change it. The setter is required in this to facilitate the modal's inherent autoclosing abilities
 * @param title The title of the modal, to display before all the other children
 * @param children The content of the modal
 */
export default function Modal({
  state: {open, setOpen},
  title,
  children,
}: React.PropsWithChildren<Props>) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useLayoutEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog
      ref={dialogRef}
      onClose={() => setOpen(false)}
      className="backdrop:bg-black/60 backdrop:backdrop-blur-sm bg-white dark:bg-gray-900 p-6 rounded-xl w-full max-w-md shadow-2xl border border-gray-200 dark:border-gray-800 m-auto"
    >
      <h2 className="text-xl font-bold mb-4 text-gray-900 dark:text-white">
        {title}
      </h2>
      {open && children}
    </dialog>
  );
}
