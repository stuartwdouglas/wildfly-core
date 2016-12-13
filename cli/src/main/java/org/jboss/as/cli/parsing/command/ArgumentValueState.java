/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.parsing.command;

import org.jboss.as.cli.parsing.BackQuotesState;
import org.jboss.as.cli.parsing.DefaultStateWithEndCharacter;
import org.jboss.as.cli.parsing.ExpressionBaseState;
import org.jboss.as.cli.parsing.QuotesState;
import org.jboss.as.cli.parsing.WordCharacterHandler;

/**
 *
 * @author Alexey Loubyansky
 */
public class ArgumentValueState extends ExpressionBaseState {

    public static final ArgumentValueState INSTANCE = new ArgumentValueState();
    public static final String ID = "PROP_VALUE";

    ArgumentValueState() {
        super(ID, false);
        this.setEnterHandler(ctx -> {
            if(ctx.getCharacter() != '=') {
                getHandler(ctx.getCharacter()).handle(ctx);
            }
        });
        enterState('[', new DefaultStateWithEndCharacter("BRACKETS", ']', false, true, enterStateHandlers));
        enterState('(', new DefaultStateWithEndCharacter("PARENTHESIS", ')', false, true, enterStateHandlers));
        enterState('{', new DefaultStateWithEndCharacter("BRACES", '}', false, true, enterStateHandlers));
        setLeaveOnWhitespace(true);
        setDefaultHandler(new WordCharacterHandler(true, false));
        enterState('"', QuotesState.QUOTES_INCLUDED_KEEP_ESCAPES);
        enterState('`', new BackQuotesState(true, false));
        setReturnHandler(ctx -> {
            if(ctx.isEndOfContent()) {
                ctx.leaveState();
            }
        });
    }

    @Override
    public boolean lockValueIndex() {
        return true;
    }
}
