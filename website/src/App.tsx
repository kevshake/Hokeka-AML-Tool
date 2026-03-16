import Hero from './sections/Hero'
import Features from './sections/Features'
import Industries from './sections/Industries'
import HowItWorks from './sections/HowItWorks'
import Stats from './sections/Stats'
import Compliance from './sections/Compliance'
import CTA from './sections/CTA'
import Footer from './sections/Footer'
import Navbar from './components/Navbar'

function App() {
  return (
    <div className="min-h-screen bg-white">
      <Navbar />
      <main>
        <Hero />
        <Stats />
        <Features />
        <HowItWorks />
        <Industries />
        <Compliance />
        <CTA />
      </main>
      <Footer />
    </div>
  )
}

export default App
