type FieldErrorProps = {
  message: string | undefined
}

export const FieldError = ({ message }: FieldErrorProps) =>
  message === undefined ? null : <p className="field-error">{message}</p>
