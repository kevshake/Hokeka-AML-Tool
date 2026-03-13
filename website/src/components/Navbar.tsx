import { useState, useEffect } from 'react'
import { Menu, X, Shield } from 'lucide-react'

const navLinks = [
  { name: 'Features', href: '#features' },
  { name: 'How It Works', href: '#how-it-works' },
  { name: 'Industries', href: '#industries' },
  { name: 'Compliance', href: '#compliance' },
]

export default function Navbar() {
  const [isScrolled, setIsScrolled] = useState(false)
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false)

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 50)
    }
    window.addEventListener('scroll', handleScroll)
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  return (
    <nav
      className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${
        isScrolled
          ? 'bg-white/95 backdrop-blur-md shadow-lg'
          : 'bg-transparent'
      }`}
    >
      <div className="section-padding">
        <div className="flex items-center justify-between h-20 max-w-7xl mx-auto">
          {/* Logo */}
          <a href="#" className="flex items-center gap-2 group">
            <div className={`p-2 rounded-lg transition-colors ${
              isScrolled ? 'bg-burgundy-800' : 'bg-white/10'
            }`}>
              <Shield className={`w-6 h-6 ${
                isScrolled ? 'text-gold-400' : 'text-gold-400'
              }`} />
            </div>
            <span className={`text-2xl font-bold transition-colors ${
              isScrolled ? 'text-burgundy-900' : 'text-white'
            }`}>
              HOKEKA
            </span>
          </a>

          {/* Desktop Navigation */}
          <div className="hidden md:flex items-center gap-8">
            {navLinks.map((link) => (
              <a
                key={link.name}
                href={link.href}
                className={`text-sm font-medium transition-colors hover:text-gold-500 ${
                  isScrolled ? 'text-gray-700' : 'text-white/90'
                }`}
              >
                {link.name}
              </a>
            ))}
            <a
              href="https://aml.hokeka.com"
              className="btn-primary text-sm"
            >
              Access Dashboard
            </a>
          </div>

          {/* Mobile Menu Button */}
          <button
            className="md:hidden p-2"
            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
          >
            {isMobileMenuOpen ? (
              <X className={isScrolled ? 'text-burgundy-900' : 'text-white'} />
            ) : (
              <Menu className={isScrolled ? 'text-burgundy-900' : 'text-white'} />
            )}
          </button>
        </div>

        {/* Mobile Menu */}
        {isMobileMenuOpen && (
          <div className="md:hidden absolute top-full left-0 right-0 bg-white shadow-lg border-t">
            <div className="section-padding py-4 space-y-4">
              {navLinks.map((link) => (
                <a
                  key={link.name}
                  href={link.href}
                  className="block text-gray-700 hover:text-burgundy-800 font-medium"
                  onClick={() => setIsMobileMenuOpen(false)}
                >
                  {link.name}
                </a>
              ))}
              <a
                href="https://aml.hokeka.com"
                className="btn-primary block text-center text-sm"
              >
                Access Dashboard
              </a>
            </div>
          </div>
        )}
      </div>
    </nav>
  )
}
