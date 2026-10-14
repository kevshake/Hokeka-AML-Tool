import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './index.css'
import './assets.css'

/** Mount the marketing SPA (StrictMode disabled to avoid double effect work in dev perf runs). */
ReactDOM.createRoot(document.getElementById('root')!).render(<App />)
