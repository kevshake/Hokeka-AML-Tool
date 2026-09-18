import { Menu, X } from 'lucide-react'
import { useEffect, useState } from 'react'

export default function Navbar() {
  const [open, setOpen] = useState(false)
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  return <header className={`navbar ${scrolled ? 'scrolled' : ''}`}><div className="container nav-inner"><a className="brand" href="#home"><img src="/hokeka-logo.png" alt="Hokeka"/><span>Hokeka</span></a><nav className={`nav-links ${open ? 'open' : ''}`} onClick={() => setOpen(false)}><a href="#solutions">Solutions</a><a href="#platform">Platform</a><a href="#integrations">Integrations</a><a href="#contact">Contact</a></nav><div className="nav-actions"><a className="button quiet login" href="http://localhost:5173/login">Client login</a><a className="button demo" href="mailto:info@hokeka.com?subject=Hokeka%20platform%20demo">Request demo</a><button className="nav-menu" type="button" aria-label={open ? 'Close menu' : 'Open menu'} onClick={() => setOpen(!open)}>{open ? <X size={19}/> : <Menu size={19}/>}</button></div></div></header>
}
