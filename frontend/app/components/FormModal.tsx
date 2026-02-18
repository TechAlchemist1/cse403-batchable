import Button, {type Style} from './Button';
import type {ModalState} from './Modal';
import Modal from './Modal';

interface FormData {
  [k: string]: string;
}

interface Props<T extends FormData> {
  title: string;
  state: ModalState;
  confirm?: string;
  style?: Style;
  apply: (data: T) => void;
}

/**
 * Describes a modal which consists entirely of a form, and which closes upon having its changes realized.
 * This may be updated to display a loading state while `apply` is resolving in the future.
 * Examples on how to use this can be found in {@link EditOrderModal}, {@link EditDriverModal}, and {@link AddOrderModal}.
 * @param title The header text for the modal
 * @param state The ModalState associated with this modal
 * @param confirm? The text in the 'confirm'-esque button. Default is 'Apply Changes'
 * @param style? The button style of the 'confirm' button. Default is 'blue'
 * @param apply A function (possibly async) to call when the 'confirm' button is pressed. It is passed the data from the form as a Record<string, string>
 * @param children The form fields and other content to appear inside the modal
 */
export default function FormModal<T extends FormData>({
  title,
  state,
  confirm = 'Apply Changes',
  style,
  apply,
  children,
}: React.PropsWithChildren<Props<T>>) {
  const handleSubmit = (e: React.SubmitEvent<HTMLFormElement>) => {
    const formData = new FormData(e.currentTarget);
    const data = Object.fromEntries(formData) as T;
    apply(data);
  };
  return (
    <Modal state={state} title={title}>
      <form onSubmit={handleSubmit} className="space-y-4" method="dialog">
        {children}
        <div className="mt-6 flex gap-3 w-full">
          <Button
            style="blank"
            onClick={() => state.setOpen(false)}
            tw="flex-0 grow"
          >
            Cancel
          </Button>
          <Button style={style} submit tw="flex-0 grow">
            {confirm}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
