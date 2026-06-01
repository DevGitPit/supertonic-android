import zipfile
import xml.etree.ElementTree as ET
import os
import sys

if len(sys.argv) < 2:
    print("Usage: python inspect_epub_structure.py <path_to_epub>")
    sys.exit(1)

epub_path = sys.argv[1]

if not os.path.exists(epub_path):
    print(f"File not found: {epub_path}")
    sys.exit(1)

with zipfile.ZipFile(epub_path, 'r') as z:
    # 1. Find container.xml to locate OPF
    container_xml = z.read("META-INF/container.xml")
    root = ET.fromstring(container_xml)
    ns = {"ns": "urn:oasis:names:tc:opendocument:xmlns:container"}
    opf_path = root.find(".//ns:rootfile", ns).attrib["full-path"]
    print(f"OPF Path: {opf_path}")
    
    # 2. Read OPF
    opf_content = z.read(opf_path)
    opf_dir = os.path.dirname(opf_path)
    
    # Parse OPF XML
    opf_root = ET.fromstring(opf_content)
    # The OPF XML uses the opf namespace (sometimes default namespace)
    # Let's find namespace from the root tag
    ns_opf = ""
    if opf_root.tag.startswith("{"):
        ns_opf = opf_root.tag.split("}")[0] + "}"
    
    print(f"OPF Namespace: {ns_opf}")
    
    # Extract manifest items
    manifest = {}
    for item in opf_root.findall(f".//{ns_opf}item"):
        item_id = item.attrib.get("id")
        item_href = item.attrib.get("href")
        manifest[item_id] = item_href
        
    print(f"Manifest size: {len(manifest)} items")
    
    # Extract spine items
    spine = []
    for itemref in opf_root.findall(f".//{ns_opf}itemref"):
        idref = itemref.attrib.get("idref")
        href = manifest.get(idref)
        spine.append((idref, href))
        
    print(f"\nSpine: {len(spine)} items")
    for i, (idref, href) in enumerate(spine):
        # Print a few to inspect
        if i < 15 or i > len(spine) - 5:
            print(f"  Spine[{i}]: idref={idref}, href={href}")
        elif i == 15:
            print("  ...")

    # Find TOC file (usually NCX or Nav document)
    # NCX is referenced in spine/manifest
    toc_id = opf_root.find(f".//{ns_opf}spine").attrib.get("toc")
    toc_href = manifest.get(toc_id) if toc_id else None
    print(f"\nTOC NCX ID: {toc_id}, Href: {toc_href}")
    
    if toc_href:
        toc_full_path = os.path.join(opf_dir, toc_href).replace("\\", "/")
        print(f"TOC Full Path: {toc_full_path}")
        if toc_full_path in z.namelist():
            toc_content = z.read(toc_full_path)
            toc_root = ET.fromstring(toc_content)
            ns_ncx = ""
            if toc_root.tag.startswith("{"):
                ns_ncx = toc_root.tag.split("}")[0] + "}"
            
            # Print TOC points recursively with indentation levels
            print("\nTOC entries:")
            nav_map = toc_root.find(f".//{ns_ncx}navMap")
            if nav_map is not None:
                def print_nav_points(element, level):
                    for child in element:
                        if child.tag == f"{ns_ncx}navPoint":
                            label_el = child.find(f"{ns_ncx}navLabel/{ns_ncx}text")
                            content_el = child.find(f"{ns_ncx}content")
                            label = label_el.text if label_el is not None else "No Label"
                            src = content_el.attrib.get("src") if content_el is not None else "No Src"
                            indent = "  " * level
                            print(f"{indent}- Label='{label}', Src='{src}'")
                            print_nav_points(child, level + 1)
                print_nav_points(nav_map, 0)
            else:
                nav_points = toc_root.findall(f".//{ns_ncx}navPoint")
                print(f"Found {len(nav_points)} navPoints in NCX")
                for i, nav in enumerate(nav_points):
                    label_el = nav.find(f".//{ns_ncx}text")
                    content_el = nav.find(f".//{ns_ncx}content")
                    label = label_el.text if label_el is not None else "No Label"
                    src = content_el.attrib.get("src") if content_el is not None else "No Src"
                    print(f"  TOC[{i}]: Label='{label}', Src='{src}'")
        else:
            print("TOC file not found in zip.")
    else:
        # Check if there is an EPUB3 nav file
        # Nav file has properties="nav"
        nav_href = None
        for item in opf_root.findall(f".//{ns_opf}item"):
            if "nav" in item.attrib.get("properties", "").split():
                nav_href = item.attrib.get("href")
                break
        print(f"EPUB3 Nav Href: {nav_href}")
        if nav_href:
            nav_full_path = os.path.join(opf_dir, nav_href).replace("\\", "/")
            print(f"Nav Full Path: {nav_full_path}")
            if nav_full_path in z.namelist():
                print("EPUB3 Nav file exists in zip. (Can be parsed if needed)")
