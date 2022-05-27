/*
   Copyright 2006 Benjamin Livshits

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

/**
    @author Benjamin Livshits <livshits@cs.stanford.edu>
    
    $Id: Sanitizers4.java,v 1.6 2006/04/04 20:00:41 livshits Exp $
 */
package securibench.micro.sanitizers;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import securibench.micro.BasicTestCase;
import securibench.micro.MicroTestCase;

/** 
 *  @servlet description="buggy sanitizer" 
 *  @servlet vuln_count = "2" 
 *  */
public class Sanitizers4 extends BasicTestCase implements MicroTestCase {
    private static final String FIELD_NAME = "name";
    private PrintWriter writer;

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String name = req.getParameter(FIELD_NAME);
        String clean = clean(name);
        
        writer = resp.getWriter();
        resp.setContentType("text/html");
        
        writer.println("<html>" + name  + "</html>");                  /* BAD */
        writer.println("<html>" + clean + "</html>");                  /* BAD */        
    }
    
    /** 
     * buggy javascript sanitization routine 
     * */
    private String clean(String name) {
        StringBuffer buf = new StringBuffer();
        for(int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            switch (ch) {
                case '&':
                    buf.append("&amp;"); break;
                default:
                    buf.append(ch); break;
            }
        }
        
        return buf.toString();
    }

    public String getDescription() {
        return "buggy sanitizer";
    }
    
    public int getVulnerabilityCount() {
        return 2;
    }
}