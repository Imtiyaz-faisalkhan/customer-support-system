import { Component } from 'react'

// Catches a failed lazy-loaded page (e.g. its code chunk can't be downloaded after a dev-server restart or a
// deploy) so the app shows a recoverable message instead of a blank screen. Remounts fresh on navigation.
class PageLoadBoundary extends Component {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  render() {
    if (this.state.failed) {
      return (
        <div className="error-banner error-with-action">
          <span>This page failed to load.</span>
          <button type="button" onClick={() => window.location.reload()}>
            Reload
          </button>
        </div>
      )
    }
    return this.props.children
  }
}

export default PageLoadBoundary
