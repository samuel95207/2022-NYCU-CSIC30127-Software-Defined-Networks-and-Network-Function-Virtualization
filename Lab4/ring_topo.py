from mininet.topo import Topo

class Ring_Topo( Topo ):
    def __init__( self ):
        Topo.__init__( self )

        # Add hosts
        h1 = self.addHost( 'h1' )
        h2 = self.addHost( 'h2' )
        h3 = self.addHost( 'h3' )
        h4 = self.addHost( 'h4' )
        h5 = self.addHost( 'h5' )
        h6 = self.addHost( 'h6' )
        h7 = self.addHost( 'h7' )
        h8 = self.addHost( 'h8' )
        h9 = self.addHost( 'h9' )
        h10 = self.addHost( 'h10' )
        h11 = self.addHost( 'h11' )
        h12 = self.addHost( 'h12' )
        


        # Add switches
        s1 = self.addSwitch( 's1' )
        s2 = self.addSwitch( 's2' )
        s3 = self.addSwitch( 's3' )
        s4 = self.addSwitch( 's4' )
        s5 = self.addSwitch( 's5' )
        s6 = self.addSwitch( 's6' )


        
        # Add links
        self.addLink( s1, s2 )
        self.addLink( s2, s3 )
        self.addLink( s2, s6 )
        self.addLink( s3, s4 )
        self.addLink( s4, s5 )
        self.addLink( s6, s4 )


        self.addLink( h1, s1 )
        self.addLink( h2, s1 )
        self.addLink( h3, s2 )
        self.addLink( h4, s2 )
        self.addLink( h5, s3 )
        self.addLink( h6, s3 )
        self.addLink( h7, s4 )
        self.addLink( h8, s4 )
        self.addLink( h9, s5 )
        self.addLink( h10, s5 )
        self.addLink( h11, s6 )
        self.addLink( h12, s6 )




topos = { 'ring': Ring_Topo }