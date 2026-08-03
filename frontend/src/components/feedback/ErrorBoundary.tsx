import { Component, type ErrorInfo, type ReactNode } from 'react';
import { RotateCw } from 'lucide-react';
import { Button } from '@ui/index';
import { logger } from '@lib/logging/logger';
import { EmptyState } from './EmptyState';

interface Props {
  children: ReactNode;
  /** Names the region so a crash in one panel does not blank the whole shell. */
  region?: string;
  fallback?: ReactNode;
}

interface State {
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  override state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  override componentDidCatch(error: Error, info: ErrorInfo): void {
    logger.error('Render crash', {
      errorClass: error.name,
      path: this.props.region ?? 'root',
    });
    if (import.meta.env.DEV) console.error(error, info.componentStack);
  }

  private reset = () => this.setState({ error: null });

  override render(): ReactNode {
    if (!this.state.error) return this.props.children;
    if (this.props.fallback) return this.props.fallback;

    return (
      <EmptyState
        heading="This section stopped responding"
        description="The rest of the page is unaffected. Reloading this panel usually clears it."
        action={
          <Button variant="secondary" onClick={this.reset}>
            <RotateCw aria-hidden />
            Reload this section
          </Button>
        }
      />
    );
  }
}
