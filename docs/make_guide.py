# Generates COLLABO-Admin-Guide.pdf — ops tutorial for Deus
from fpdf import FPDF

class Guide(FPDF):
    def header(self):
        if self.page_no() == 1:
            return
        self.set_font("helvetica", "I", 8)
        self.set_text_color(120)
        self.cell(0, 8, "COLLABO - Admin & Database Operations Guide", align="C", new_x="LMARGIN", new_y="NEXT")
        self.ln(2)

    def footer(self):
        self.set_y(-15)
        self.set_font("helvetica", "I", 8)
        self.set_text_color(120)
        self.cell(0, 10, f"Page {self.page_no()}", align="C")

pdf = Guide()
pdf.set_auto_page_break(auto=True, margin=20)
pdf.add_page()

def title(t):
    pdf.set_font("helvetica", "B", 22)
    pdf.set_text_color(30, 30, 30)
    pdf.multi_cell(0, 10, t, new_x="LMARGIN", new_y="NEXT")

def h1(t):
    pdf.ln(4)
    pdf.set_font("helvetica", "B", 15)
    pdf.set_text_color(20, 60, 140)
    pdf.multi_cell(0, 8, t, new_x="LMARGIN", new_y="NEXT")
    pdf.ln(1)

def h2(t):
    pdf.ln(2)
    pdf.set_font("helvetica", "B", 12)
    pdf.set_text_color(40, 40, 40)
    pdf.multi_cell(0, 7, t, new_x="LMARGIN", new_y="NEXT")

def body(t):
    pdf.set_font("helvetica", "", 10.5)
    pdf.set_text_color(30, 30, 30)
    pdf.multi_cell(0, 5.5, t, new_x="LMARGIN", new_y="NEXT")
    pdf.ln(1)

def bullet(t):
    pdf.set_font("helvetica", "", 10.5)
    pdf.set_text_color(30, 30, 30)
    pdf.multi_cell(0, 5.5, "  -  " + t, new_x="LMARGIN", new_y="NEXT")

def code(lines):
    pdf.set_fill_color(35, 35, 40)
    pdf.set_font("courier", "", 9)
    pdf.set_text_color(220, 220, 220)
    for ln in lines:
        pdf.cell(0, 5, " " + ln, fill=True, new_x="LMARGIN", new_y="NEXT")
    pdf.ln(2)

def warn(t):
    pdf.set_font("helvetica", "B", 10.5)
    pdf.set_text_color(170, 30, 30)
    pdf.multi_cell(0, 5.5, t, new_x="LMARGIN", new_y="NEXT")
    pdf.ln(1)

def table(headers, rows, widths):
    pdf.set_font("helvetica", "B", 9.5)
    pdf.set_fill_color(230, 235, 245)
    pdf.set_text_color(30, 30, 30)
    for h, w in zip(headers, widths):
        pdf.cell(w, 6.5, " " + h, border=1, fill=True)
    pdf.ln()
    pdf.set_font("helvetica", "", 9.5)
    for row in rows:
        for c, w in zip(row, widths):
            pdf.cell(w, 6, " " + c, border=1)
        pdf.ln()
    pdf.ln(2)

# ---------- COVER ----------
pdf.ln(40)
title("COLLABO")
title("Admin & Database Operations Guide")
pdf.ln(6)
pdf.set_font("helvetica", "", 12)
pdf.set_text_color(90, 90, 90)
pdf.multi_cell(0, 7, new_x="LMARGIN", new_y="NEXT", text="Your closed funnel: the admin panel, Adminer, and how to deploy without breaking anything.")
pdf.ln(30)
pdf.set_font("helvetica", "I", 10)
pdf.multi_cell(0, 6, new_x="LMARGIN", new_y="NEXT", text="Prepared for Deus - collabo-app (Deushomo1st/collabo-app)\nVPS: 162.35.125.153 - stack at /opt/collabo")

# ---------- 1. BIG PICTURE ----------
pdf.add_page()
h1("1. The Big Picture")
body("You have TWO doors into your system's data. They serve different purposes:")
table(
    ["Door", "What it is", "How you reach it"],
    [
        ["admin.html panel", "Custom dashboard made for your app", "Public URL + admin key"],
        ["Adminer", "Generic raw database tool (open source)", "SSH tunnel only - invisible to internet"],
    ],
    [45, 70, 70],
)
h2("Why it is safe (the closed funnel)")
bullet("Postgres port 5432 is NOT exposed on the VPS at all. Only containers can reach it.")
bullet("Adminer only listens on the VPS's internal localhost (127.0.0.1). The internet cannot see it.")
bullet("Every /api/admin endpoint demands your ADMIN_KEY. Wrong key = 403. No key on server = fails closed (503).")
bullet("Public registration can only create USER accounts. ADMIN accounts are born only via the panel.")
bullet("Secrets live only in /opt/collabo/.env on the VPS. That file never enters git.")

# ---------- 2. ADMIN PANEL ----------
h1("2. Door 1 - The Admin Panel")
h2("Opening it")
code(["http://162.35.125.153:8080/admin.html"])
body("The page is not linked anywhere on the public site - you type the URL directly.")
body("First screen is a lock asking for the admin key. Paste your ADMIN_KEY (the one in the VPS .env). It is kept in sessionStorage only: closing the tab logs you out. Nothing is saved on disk.")
h2("What you can do")
bullet("USERS table - see every user: email, username, role, creation date.")
bullet("Delete a user - red button per row, asks for confirmation first.")
bullet("Change a role - dropdown in the row. Confirms before promoting to ADMIN. Refuses to demote or delete the ONLY remaining ADMIN (self-lockout guard).")
bullet("Create a user - form at the top: email, username, password, role. This is now the ONLY way ADMIN accounts get created.")
bullet("DATABASE section - every table with its live row count.")
bullet("Clean table - wipes a table for testing. You must TYPE the table name to confirm. IDs restart from scratch.")
h2("What the error messages mean")
table(
    ["Message", "Meaning", "Fix"],
    [
        ["Wrong key", "ADMIN_KEY did not match", "Re-check .env on the VPS, paste again"],
        ["Admin key not configured", "Server .env is missing ADMIN_KEY", "Add it, then: docker compose up -d --force-recreate backend"],
    ],
    [45, 65, 75],
)

# ---------- 3. ADMINER ----------
pdf.add_page()
h1("3. Door 2 - Adminer (raw database access)")
h2("What Adminer is")
body("Adminer is a free open-source database manager (adminer.org, around since 2007). It is NOT something built for you - it is a standard industry tool. You are running its official Docker image on your VPS.")
h2("The one concept: the SSH tunnel")
body("Adminer listens only on the VPS's own localhost, so http://162.35.125.153:8081 will NEVER work from your laptop. The tunnel forwards a port on YOUR machine into the VPS:")
code(["ssh -N -L 18081:127.0.0.1:8081 deus@162.35.125.153"])
bullet("It asks for the password of the 'deus' Linux user on the VPS (NOT the admin key).")
bullet("After the password, the window looks FROZEN. No prompt, no output. That means it is WORKING. Leave it open.")
bullet("18081 = the port on your laptop. 8081 = Adminer inside the VPS. Only the first number may change.")
h2("Then in the browser")
code(["http://localhost:18081"])
table(
    ["Field", "Value"],
    [
        ["System", "PostgreSQL"],
        ["Server", "db   (not localhost! 'db' is the container name)"],
        ["Username", "collabo_user"],
        ["Password", "collabo_password"],
        ["Database", "collabo"],
    ],
    [40, 145],
)
h2("Using it")
bullet("Click the 'users' table, then 'Select data' - every registered user as rows.")
bullet("Edit a row: click 'edit' on its left. Change role USER -> ADMIN, save.")
bullet("Delete rows: tick checkboxes, 'Delete' button at the bottom.")
bullet("SQL command (top menu): run any raw SQL.")
warn("Adminer has NO guardrails. 'Drop' and 'Truncate' wipe things permanently. The panel is for routine work; Adminer is for popping the hood.")
body("When done: close the git-bash window running the tunnel. The doorway disappears.")

# ---------- 4. DEPLOYING ----------
pdf.add_page()
h1("4. Deploying Changes")
h2("The pipeline")
body("Pushing to main on GitHub triggers the Action: build image -> push to GHCR -> SSH into VPS -> recreate the backend container. Watch it at github.com/Deushomo1st/collabo-app/actions.")
code(['cd "/c/Users/Deus/Desktop/Springworld/collabo-backend"', "git push origin main"])
h2("Important quirks")
bullet("The pipeline recreates ONLY the backend. New compose services (like adminer) need a manual first start on the VPS.")
bullet("/opt/collabo on the VPS is NOT a git repo - the pipeline's 'git pull' line does nothing. The VPS compose file (docker-compose.yml) is edited by hand.")
bullet("If the backend container will not start after a deploy, check .env first: both RESEND_API_KEY and ADMIN_KEY must be present.")
h2("Useful VPS commands")
code([
    "ssh deus@162.35.125.153",
    "cd /opt/collabo",
    "docker ps                              # what is running",
    "docker compose logs backend --tail 50  # backend logs",
    "docker compose up -d --force-recreate backend",
    "docker compose up -d adminer",
])

# ---------- 5. MANAGER.SH ----------
h1("5. manager.sh (your VPS helper script)")
table(
    ["Command", "Does"],
    [
        ["./manager.sh start", "docker compose up -d"],
        ["./manager.sh stop", "docker compose down"],
        ["./manager.sh restart", "down + up"],
        ["./manager.sh status", "running containers"],
        ["./manager.sh logs", "last 50 backend log lines"],
    ],
    [60, 125],
)
warn("./manager.sh add-admin is DEPRECATED. It used public registration to make ADMINs - that hole is now closed. Create admins in the admin.html panel instead.")

# ---------- 6. TROUBLESHOOTING ----------
h1("6. Troubleshooting")
table(
    ["Symptom", "Cause / Fix"],
    [
        ["ssh: Could not resolve hostname", "Missing @ between user and IP: deus@162.35.125.153"],
        ["bind [127.0.0.1]:8081: Permission denied", "Port busy on YOUR laptop. Change the local port: -L 18081:... or -L 19091:..."],
        ["channel: open failed: Connection refused", "Tunnel OK, but Adminer not running on VPS: docker compose up -d adminer"],
        ["Tunnel window looks frozen", "It is working. Leave it open."],
        ["no such service: adminer", "VPS compose file outdated - add the adminer block by hand"],
        ["Browser shows a blue animated page", "That IS Adminer's default skin. You are in the right place."],
        ["403 / Wrong key at the panel", "ADMIN_KEY mismatch - check /opt/collabo/.env"],
    ],
    [75, 110],
)

# ---------- 7. QUICK REFERENCE ----------
h1("7. Quick Reference Card")
code([
    "# Panel (anywhere)",
    "http://162.35.125.153:8080/admin.html",
    "",
    "# Adminer (tunnel first)",
    "ssh -N -L 18081:127.0.0.1:8081 deus@162.35.125.153",
    "http://localhost:18081   ->  PostgreSQL / db / collabo_user / collabo_password / collabo",
    "",
    "# Deploy",
    'cd "/c/Users/Deus/Desktop/Springworld/collabo-backend" && git push origin main',
    "",
    "# VPS",
    "ssh deus@162.35.125.153 && cd /opt/collabo && docker ps",
])

import os
out = r"C:\Users\Deus\Desktop\Springworld\collabo-backend\docs\COLLABO-Admin-Guide.pdf"
os.makedirs(os.path.dirname(out), exist_ok=True)
pdf.output(out)
print("OK", out, os.path.getsize(out), "bytes")
