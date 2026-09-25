from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    PageTemplate,
    Paragraph,
    PageBreak,
    Spacer,
    Table,
    TableStyle,
    Image,
    KeepTogether,
)

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "iam" / "iam-bounded-context.pdf"
ASSETS = ROOT / "docs" / "diagrams" / "rendered"

PAGE = landscape(A4)
styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="TitleBig", parent=styles["Title"], fontName="Helvetica-Bold", fontSize=25, leading=30, alignment=TA_CENTER, textColor=colors.HexColor("#0F172A"), spaceAfter=12))
styles.add(ParagraphStyle(name="Subtitle", parent=styles["Normal"], fontSize=12, leading=17, alignment=TA_CENTER, textColor=colors.HexColor("#475569"), spaceAfter=18))
styles.add(ParagraphStyle(name="H1x", parent=styles["Heading1"], fontSize=18, leading=22, textColor=colors.HexColor("#0F172A"), spaceBefore=8, spaceAfter=10))
styles.add(ParagraphStyle(name="H2x", parent=styles["Heading2"], fontSize=13, leading=17, textColor=colors.HexColor("#1E3A8A"), spaceBefore=8, spaceAfter=7))
styles.add(ParagraphStyle(name="Bodyx", parent=styles["BodyText"], fontSize=9.3, leading=13.5, textColor=colors.HexColor("#334155"), spaceAfter=6))
styles.add(ParagraphStyle(name="Smallx", parent=styles["BodyText"], fontSize=7.8, leading=10, textColor=colors.HexColor("#334155")))
styles.add(ParagraphStyle(name="TableHead", parent=styles["BodyText"], fontName="Helvetica-Bold", fontSize=8.3, leading=10, textColor=colors.white))
styles.add(ParagraphStyle(name="Caption", parent=styles["BodyText"], fontSize=8, leading=10, alignment=TA_CENTER, textColor=colors.HexColor("#64748B"), spaceBefore=4, spaceAfter=8))


def p(text, style="Bodyx"):
    return Paragraph(text.replace("&", "&amp;"), styles[style])


def table(title, rows, widths):
    data = [[p("Clase / componente", "TableHead"), p("Tipo", "TableHead"), p("Propósito", "TableHead")]]
    data += [[p(str(a), "Smallx"), p(str(b), "Smallx"), p(str(c), "Smallx")] for a, b, c in rows]
    t = Table(data, colWidths=widths, repeatRows=1, hAlign="LEFT")
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1E3A8A")),
        ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#CBD5E1")),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FAFC")]),
    ]))
    return [p(title, "H2x"), t, Spacer(1, 6)]


domain = [
    ("User", "Aggregate Root", "Identidad autenticable, roles y vínculo con compañía."),
    ("BuyerCompany", "Aggregate Root", "Empresa compradora y sus datos de contacto."),
    ("ProviderCompany", "Aggregate Root", "Empresa proveedora y combustibles ofrecidos."),
    ("Role", "Entity", "Rol persistible asociado a un usuario."),
    ("Roles", "Value Object / enum", "ROLE_BUYER y ROLE_PROVIDER."),
    ("SignUpCommand / SignInCommand", "Command", "Casos de uso de registro e inicio de sesión."),
    ("Create*CompanyCommand", "Command", "Creación de perfiles de empresa."),
    ("Get*Query", "Query", "Consultas de usuarios y compañías."),
    ("*Repository", "Repository Port", "Puertos de persistencia del dominio."),
]
application = [
    ("UserCommandService / Impl", "Command Service", "Sign-in, sign-up, hashing, roles y vínculos."),
    ("UserQueryService / Impl", "Query Service", "Consulta de usuarios para directorio y seguridad."),
    ("BuyerCompany*Service", "Command / Query", "Casos de uso de compañía compradora."),
    ("ProviderCompany*Service", "Command / Query", "Casos de uso de compañía proveedora."),
    ("RoleCommandServiceImpl", "Command Handler", "Siembra de roles base."),
    ("PasswordResetService", "Application Service", "Token hash, correo y cambio de contraseña."),
    ("HashingService", "Outbound Port", "Contrato de BCrypt."),
    ("TokenService", "Outbound Port", "Contrato de JWT."),
]
infrastructure = [
    ("*RepositoryImpl", "Repository Adapter", "Adaptan Domain a Spring Data JPA."),
    ("*PersistenceEntity", "JPA Entity", "Tablas users, roles y compañías."),
    ("PasswordResetTokenEntity", "JPA Entity", "Hash, usuario y expiración del reset."),
    ("*PersistenceRepository", "Spring Data JPA", "Consultas de persistencia."),
    ("PasswordResetTokenRepository", "Spring Data JPA", "Tokens válidos, bloqueo y consumo único."),
    ("*PersistenceAssembler", "Mapper", "Conversión Domain <-> JPA."),
    ("WebSecurityConfiguration", "Security Configuration", "Endpoints públicos, JWT y stateless."),
    ("BearerAuthorizationRequestFilter", "JWT Filter", "Carga autenticación desde Bearer token."),
    ("UserDetailsServiceImpl", "Security Adapter", "Traduce usuario a Spring Security."),
    ("CurrentUserAccess", "Ownership Policy", "Roles y ownership por recurso."),
    ("HashingServiceImpl / TokenServiceImpl", "Adapters", "BCrypt y JWT concretos."),
]
interfaces = [
    ("AuthenticationController", "REST Controller", "Sign-in, sign-up y password reset."),
    ("UsersController", "REST Controller", "Directorio administrativo de usuarios."),
    ("BuyerCompaniesController", "REST Controller", "Operaciones de compradores."),
    ("ProviderCompaniesController", "REST Controller", "Operaciones de proveedores."),
    ("DirectoryController", "REST Controller", "Consultas de directorio."),
    ("*Resource", "DTO record", "JSON de entrada/salida y validación."),
    ("*ResourceFromEntityAssembler", "Assembler", "Domain -> REST."),
    ("*CommandFromResourceAssembler", "Assembler", "REST -> Application command."),
    ("IamContextFacade", "ACL", "Acceso controlado para otros módulos."),
]


class Report(BaseDocTemplate):
    def __init__(self, filename):
        super().__init__(filename, pagesize=PAGE, leftMargin=16 * mm, rightMargin=16 * mm, topMargin=14 * mm, bottomMargin=13 * mm, title="Bounded Context IAM - FullTank")
        frame = Frame(self.leftMargin, self.bottomMargin, self.width, self.height, id="normal")
        self.addPageTemplates([PageTemplate(id="main", frames=frame, onPage=footer)])


def footer(canvas, doc):
    canvas.saveState()
    canvas.setStrokeColor(colors.HexColor("#CBD5E1"))
    canvas.line(doc.leftMargin, 10 * mm, PAGE[0] - doc.rightMargin, 10 * mm)
    canvas.setFont("Helvetica", 7.5)
    canvas.setFillColor(colors.HexColor("#64748B"))
    canvas.drawString(doc.leftMargin, 6.5 * mm, "FullTank Platform - Bounded Context IAM")
    canvas.drawRightString(PAGE[0] - doc.rightMargin, 6.5 * mm, f"Página {doc.page}")
    canvas.restoreState()


story = []
story += [Spacer(1, 18 * mm), p("Bounded Context IAM", "TitleBig"), p("FullTank Platform | IAM legacy v1", "Subtitle")]
story += [p("Documento técnico", "H1x"), p("Este informe y sus diagramas describen la parte legacy v1 de IAM. El modelo v2 incluye Organization y Membership para resolver el tenant; ver docs/api-ledger/T04-A-organization-membership.md y T04-B-onboarding-invitations.md. La organización sigue las capas Domain, Application, Infrastructure e Interfaces.")]
story += [p("Estado", "H2x"), p("IAM ya está implementado: autenticación JWT, usuarios, roles, compañías compradoras/proveedoras, ownership de recursos y recuperación de contraseña con token de un solo uso. El alta crea usuario y compañía en una transacción y no permite que el cliente elija IDs de compañías existentes."), Spacer(1, 5)]
story += [p("Capacidades principales", "H2x")] + table("", [
    ("Inicio de sesión", "REST", "sign-in devuelve JWT, roles y company/provider ID."),
    ("Registro", "REST + transacción", "sign-up crea usuario y perfil empresarial nuevo."),
    ("Recuperación", "REST + SMTP", "request/confirm con hash, expiración y consumo único."),
    ("Autorización", "Security", "Bearer filter y CurrentUserAccess."),
    ("Directorio", "REST", "Usuarios y compañías para consultas protegidas."),
], [45*mm, 42*mm, 170*mm])
story += [PageBreak()]

for image, caption, width in [
    ("iam-bounded-context.png", "Figura 1. Límites, dependencias y relaciones del bounded context IAM.", 255 * mm),
    ("iam-layer-overview.png", "Figura 2. Vista de capas y componentes principales.", 70 * mm),
]:
    story += [p(caption.split(".", 1)[0], "H1x")]
    img = Image(str(ASSETS / image), width=width, height=1)
    ratio = img.imageHeight / img.imageWidth
    img.drawHeight = width * ratio
    img.height = img.drawHeight
    story += [img, p(caption, "Caption"), PageBreak()]

story += [p("Domain Layer", "H1x")]
story += table("Elementos del dominio", domain, [72*mm, 46*mm, 139*mm])
story += [PageBreak(), p("Application Layer", "H1x")]
story += table("Servicios, handlers y puertos", application, [72*mm, 46*mm, 139*mm])
story += [PageBreak(), p("Infrastructure Layer", "H1x")]
story += table("Adaptadores, persistencia y seguridad", infrastructure, [72*mm, 46*mm, 139*mm])
story += [PageBreak(), p("Interfaces Layer", "H1x")]
story += table("REST, DTOs y ACL", interfaces, [72*mm, 46*mm, 139*mm])
story += [PageBreak(), p("Seguridad y verificación", "H1x")]
for bullet in [
    "El secreto JWT es obligatorio mediante AUTHORIZATION_JWT_SECRET.",
    "Los endpoints de autenticación y documentación son públicos; el resto requiere autenticación.",
    "Compradores y proveedores solo acceden a recursos de su compañía o proveedor.",
    "El reset responde de forma neutra para cuentas existentes y desconocidas.",
    "El token se guarda como SHA-256, expira en 30 minutos y se elimina al consumirse.",
    "SMTP se configura con SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, SMTP_AUTH, SMTP_STARTTLS y MAIL_FROM.",
]:
    story.append(p("• " + bullet))
story += [p("Artefactos reproducibles", "H2x"), p("PlantUML está incluido en tools/plantuml/plantuml.jar y Structurizr CLI en tools/structurizr/structurizr.sh. Los fuentes son iam-bounded-context.puml, iam-layer-overview.puml, iam-class-layer.puml e iam-structurizr.dsl."), p("Comandos:", "H2x"), p("java -jar tools/plantuml/plantuml.jar -tpng -tsvg -o rendered docs/diagrams/iam-bounded-context.puml<br/>tools/structurizr/structurizr.sh validate -workspace docs/diagrams/iam-structurizr.dsl", "Smallx")]

OUT.parent.mkdir(parents=True, exist_ok=True)
Report(str(OUT)).build(story)
print(OUT)
