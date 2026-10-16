import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.tsx'
import './index.css'
import './theme/globals.css'
import './styles/globals.css'
import { installDevMocks } from './dev/installDevMock'

installDevMocks()

ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
        <App />
    </React.StrictMode>,
)
