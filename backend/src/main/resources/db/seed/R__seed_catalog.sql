-- Sample catalog for the local, test and E2E profiles (research R-04, R-23): 12 categories, 6 fixed
-- products used by the quickstart/E2E scenarios and 520 generated products. Explicit ids keep product
-- references stable when this repeatable migration runs again. Names are Polish seed data.

DELETE FROM product_image;
DELETE FROM product;
DELETE FROM category;

SET IDENTITY_INSERT category ON;
INSERT INTO category (id, name, slug, display_order) VALUES
    (1, N'Elektronika', 'electronics', 1),
    (2, N'Dom i ogród', 'home-garden', 2),
    (3, N'Kuchnia', 'kitchen', 3),
    (4, N'Sport i turystyka', 'sports', 4),
    (5, N'Książki', 'books', 5),
    (6, N'Zabawki', 'toys', 6),
    (7, N'Moda', 'fashion', 7),
    (8, N'Uroda', 'beauty', 8),
    (9, N'Motoryzacja', 'automotive', 9),
    (10, N'Biuro', 'office', 10),
    (11, N'Zwierzęta', 'pets', 11),
    (12, N'Muzyka', 'music', 12);
SET IDENTITY_INSERT category OFF;

SET IDENTITY_INSERT product ON;

-- Fixed products for the scenarios (quickstart 1.3, 1.8, 2.x, 3.x): "łódź" in the name, stock 2, 3, 5, 0
-- and an inactive product; prices within 50-200 PLN.
INSERT INTO product (id, name, description, price_minor, category_id, stock, active, version) VALUES
    (1, N'Kubek ceramiczny Łódź', N'Ręcznie malowany kubek o pojemności 350 ml. Można myć w zmywarce.', 5900, 3, 25, 1, 0),
    (2, N'Lampka biurkowa LED Duo', N'Lampka z regulacją jasności i barwy światła, zasilanie USB-C.', 12900, 10, 2, 1, 0),
    (3, N'Plecak miejski Tatra', N'Wodoodporny plecak 22 l z kieszenią na laptopa 15".', 15900, 4, 3, 1, 0),
    (4, N'Kamizelka odblaskowa Pro', N'Kamizelka z certyfikatem EN ISO 20471, rozmiar uniwersalny.', 5500, 9, 5, 1, 0),
    (5, N'Głośnik przenośny Wave', N'Głośnik Bluetooth 5.3, 12 godzin pracy na baterii.', 18900, 1, 0, 1, 0),
    (6, N'Zegar ścienny Retro', N'Produkt wycofany z oferty.', 9900, 2, 7, 0, 0);

WITH numbers AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM numbers WHERE n < 520
)
INSERT INTO product (id, name, description, price_minor, category_id, stock, active, version)
SELECT 100 + numbers.n,
       CONCAT(kind.noun, N' ', series.word, N' ', numbers.n),
       CONCAT(kind.noun, N' z serii ', series.word, N'. ', kind.description),
       1999 + (numbers.n * 3719) % 48000,
       kind.category_id,
       CASE
           WHEN numbers.n % 37 = 0 THEN 0
           WHEN numbers.n % 23 = 0 THEN 1 + numbers.n % 3
           ELSE 4 + (numbers.n * 7) % 60
       END,
       CASE WHEN numbers.n % 97 = 0 THEN 0 ELSE 1 END,
       0
FROM numbers
JOIN (VALUES
    (1, N'Słuchawki', N'Wygodne i lekkie, z etui w zestawie.'),
    (2, N'Doniczka', N'Wykonana z materiałów odpornych na mróz.'),
    (3, N'Patelnia', N'Nieprzywierająca powłoka, do każdego rodzaju kuchenki.'),
    (4, N'Bidon', N'Szczelne zamknięcie, bez BPA.'),
    (5, N'Powieść', N'Twarda oprawa, 320 stron.'),
    (6, N'Klocki', N'Zestaw dla dzieci od 4 lat.'),
    (7, N'Szalik', N'Miękka dzianina z domieszką wełny.'),
    (8, N'Krem', N'Do skóry wrażliwej, 50 ml.'),
    (9, N'Organizer', N'Pasuje do większości samochodów osobowych.'),
    (10, N'Notes', N'Papier 90 g/m², 96 kartek w kropki.'),
    (11, N'Legowisko', N'Zdejmowany pokrowiec do prania.'),
    (12, N'Kostka do gitary', N'Zestaw 12 sztuk o różnej grubości.')
) AS kind (category_id, noun, description) ON kind.category_id = numbers.n % 12 + 1
JOIN (VALUES
    (0, N'Aurora'), (1, N'Bałtyk'), (2, N'Tatry'), (3, N'Wisła'),
    (4, N'Mazury'), (5, N'Kraków'), (6, N'Sopot'), (7, N'Karkonosze')
) AS series (idx, word) ON series.idx = numbers.n % 8
OPTION (MAXRECURSION 1000);

SET IDENTITY_INSERT product OFF;

-- Main image (display_order 0) = the category image; fixed products also get a gallery image.
INSERT INTO product_image (product_id, display_order, url, alt)
SELECT product.id, 0, CONCAT('/images/', category.slug, '.svg'), product.name
FROM product
JOIN category ON category.id = product.category_id;

INSERT INTO product_image (product_id, display_order, url, alt)
SELECT id, 1, '/images/detail.svg', CONCAT(name, N' - zbliżenie')
FROM product
WHERE id <= 6;
