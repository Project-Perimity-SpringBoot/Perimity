import { Button, Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@ui/index';

export interface ConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  confirmLabel: string;
  cancelLabel?: string;
  destructive?: boolean;
  loading?: boolean;
  onConfirm: () => void;
  /** Extra content between the description and the buttons — a reason field. */
  children?: React.ReactNode;
  /** Blocks confirm while a required reason is empty. */
  confirmDisabled?: boolean;
}

/**
 * One dialog for every irreversible action in the product — revoke a pass,
 * cancel an event, deactivate a user, suspend a campus.
 *
 * The description states the consequence, not the action. "Cancel this event?"
 * tells the admin nothing they did not already know; "This revokes 312 issued
 * passes" is the sentence that changes a decision.
 */
export function ConfirmDialog({
  open, onOpenChange, title, description, confirmLabel, cancelLabel = 'Cancel',
  destructive, loading, onConfirm, children, confirmDisabled,
}: ConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        {children ? <DialogBody>{children}</DialogBody> : null}
        <DialogFooter>
          <Button variant="secondary" onClick={() => onOpenChange(false)} disabled={loading}>
            {cancelLabel}
          </Button>
          <Button
            variant={destructive ? 'danger' : 'primary'}
            onClick={onConfirm}
            loading={loading}
            disabled={confirmDisabled}
          >
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
