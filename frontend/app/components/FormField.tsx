import {useId} from 'react';

type Props = {
  type: string;
  name: string;
  label?: React.ReactNode;
} & React.InputHTMLAttributes<HTMLInputElement>;

/**
 * Represents an opinionatedly-styled (is that a word) <input> box for use in a {@link FormModal}.
 * It has the same properties as a normal <input>, with the addition of a 'label' prop.
 * @param label Label text to display along with the input. Depending on the type of input, this will be formatted differently
 */
export default function FormField(props: Props) {
  const inputId = useId();
  const horizontal = ['checkbox', 'radio'].includes(props.type);
  const label = props.label && (
    <label
      className="block text-sm font-medium mb-1 text-gray-700 dark:text-gray-300 cursor-pointer"
      htmlFor={inputId}
    >
      {props.label}
    </label>
  );
  const input = (
    <input
      className={`${horizontal ? 'relative bottom-0.5' : 'w-full'} p-2 border rounded bg-transparent border-gray-300 dark:border-gray-700`}
      {...props}
      id={inputId}
    />
  );
  return (
    <div className={horizontal ? 'flex gap-3' : ''}>
      {horizontal ? (
        <>
          {input}
          {label}
        </>
      ) : (
        <>
          {label}
          {input}
        </>
      )}
    </div>
  );
}
