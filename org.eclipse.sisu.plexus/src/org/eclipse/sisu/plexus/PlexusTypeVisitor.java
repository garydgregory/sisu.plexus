/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stuart McCulloch (Sonatype, Inc.) - initial API and implementation
 *******************************************************************************/
package org.eclipse.sisu.plexus;

import java.net.URL;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.sisu.inject.Logs;
import org.eclipse.sisu.space.ClassSpace;
import org.eclipse.sisu.space.ClassSpaceVisitor;
import org.eclipse.sisu.space.LoadedClass;
import org.eclipse.sisu.space.QualifiedTypeVisitor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * {@link ClassSpaceVisitor} that reports Plexus bean classes annotated with @{@link Component}.
 */
public final class PlexusTypeVisitor
    extends ClassVisitor
    implements ClassSpaceVisitor
{
    // ----------------------------------------------------------------------
    // Constants
    // ----------------------------------------------------------------------

    private static final String COMPONENT_DESC = Type.getDescriptor( Component.class );

    // ----------------------------------------------------------------------
    // Implementation fields
    // ----------------------------------------------------------------------

    private final ComponentAnnotationVisitor componentVisitor = new ComponentAnnotationVisitor();

    private final PlexusTypeListener plexusTypeListener;

    private final QualifiedTypeVisitor qualifiedTypeVisitor;

    private ClassSpace space;

    private String source;

    private String implementation;

    // ----------------------------------------------------------------------
    // Constructors
    // ----------------------------------------------------------------------

    public PlexusTypeVisitor( final PlexusTypeListener listener )
    {
        super( Opcodes.ASM4 );
        plexusTypeListener = listener;
        qualifiedTypeVisitor = new QualifiedTypeVisitor( listener );
    }

    // ----------------------------------------------------------------------
    // Public methods
    // ----------------------------------------------------------------------

    public void visit( final ClassSpace _space )
    {
        space = _space;
        source = _space.toString();
        qualifiedTypeVisitor.visit( _space );

        if ( Logs.TRACE_ENABLED )
        {
            QualifiedTypeVisitor.verify( _space, Component.class );
        }
    }

    public ClassVisitor visitClass( final URL url )
    {
        componentVisitor.reset();
        implementation = null;
        qualifiedTypeVisitor.visitClass( url );
        return this;
    }

    @Override
    public void visit( final int version, final int access, final String name, final String signature,
                       final String superName, final String[] interfaces )
    {
        if ( ( access & ( Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SYNTHETIC ) ) == 0 )
        {
            implementation = name.replace( '/', '.' );
        }
        qualifiedTypeVisitor.visit( version, access, name, signature, superName, interfaces );
    }

    @Override
    public AnnotationVisitor visitAnnotation( final String desc, final boolean visible )
    {
        if ( COMPONENT_DESC.equals( desc ) )
        {
            return componentVisitor;
        }
        return qualifiedTypeVisitor.visitAnnotation( desc, visible );
    }

    @Override
    public void visitEnd()
    {
        if ( null != implementation )
        {
            final Component component = componentVisitor.getComponent( space );
            if ( null != component )
            {
                final LoadedClass<?> clazz = new LoadedClass<Object>( space.loadClass( implementation ) );
                plexusTypeListener.hear( component, clazz, source );
            }
            else
            {
                qualifiedTypeVisitor.visitEnd();
            }
            implementation = null;
        }
    }

    // ----------------------------------------------------------------------
    // Component annotation scanner
    // ----------------------------------------------------------------------

    private static final class ComponentAnnotationVisitor
        extends AnnotationVisitor
    {
        private String role;

        private String hint;

        private String strategy;

        private String description;

        ComponentAnnotationVisitor()
        {
            super( Opcodes.ASM4 );
        }

        public void reset()
        {
            role = null;
            hint = Hints.DEFAULT_HINT;
            strategy = Strategies.SINGLETON;
            description = "";
        }

        @Override
        public void visit( final String name, final Object value )
        {
            if ( "role".equals( name ) )
            {
                role = ( (Type) value ).getClassName();
            }
            else if ( "hint".equals( name ) )
            {
                hint = Hints.canonicalHint( (String) value );
            }
            else if ( "instantiationStrategy".equals( name ) )
            {
                strategy = (String) value;
            }
            else if ( "description".equals( name ) )
            {
                description = (String) value;
            }
        }

        public Component getComponent( final ClassSpace space )
        {
            return null != role ? new ComponentImpl( space.loadClass( role ), hint, strategy, description ) : null;
        }
    }
}