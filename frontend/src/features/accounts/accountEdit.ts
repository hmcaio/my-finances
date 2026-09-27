/** The editable part of an account is its name and institution; both are mandatory. */
export function isAccountEditValid(name: string, institutionId: string | undefined): boolean {
  return name.trim() !== '' && institutionId !== undefined
}
